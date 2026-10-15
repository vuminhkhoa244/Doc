from contextlib import asynccontextmanager
import json

import chromadb
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from openai import OpenAI

from app import generation, indexing, rerank, retrieval
from app.config import Settings
from app.corpus import Bm25Corpus, CorpusChunk
from app.models import (
    CompleteRequest,
    CompleteResponse,
    DeleteIndexResponse,
    GenerateRequest,
    GenerateResponse,
    HealthResponse,
    IndexRequest,
    IndexResponse,
    QueryRequest,
    QueryResponse,
    SearchRequest,
    SearchResponse,
    SearchResult,
    SourceOut,
)


def _build_openai(settings: Settings) -> OpenAI:
    return OpenAI(
        api_key=settings.resolved_api_key,
        base_url=settings.resolved_base_url,
        timeout=settings.request_timeout_seconds,
        default_headers={
            "HTTP-Referer": "http://localhost",
            "X-Title": "AI Document Assistant",
        }
        if settings.resolved_provider == "openrouter"
        else None,
    )


def _build_collection(settings: Settings):
    client = chromadb.PersistentClient(path=settings.chroma_persist_dir)
    return client.get_or_create_collection(
        name=settings.collection_name,
        metadata={"hnsw:space": "cosine"},
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings()
    app.state.settings = settings
    app.state.collection = _build_collection(settings)
    app.state.corpus = Bm25Corpus()
    app.state.corpus.warm_from_collection(app.state.collection)
    app.state.openai = _build_openai(settings) if settings.resolved_api_key else None
    yield


def create_app(settings: Settings | None = None, collection=None, openai_client=None) -> FastAPI:
    @asynccontextmanager
    async def injected_lifespan(app: FastAPI):
        app.state.settings = settings or Settings()
        app.state.collection = collection if collection is not None else _build_collection(app.state.settings)
        app.state.corpus = Bm25Corpus()
        app.state.corpus.warm_from_collection(app.state.collection)
        if openai_client is not None:
            app.state.openai = openai_client
        elif app.state.settings.resolved_api_key:
            app.state.openai = _build_openai(app.state.settings)
        else:
            app.state.openai = None
        yield

    app = FastAPI(title="AI Document Assistant - AI Service", lifespan=injected_lifespan)
    _register_routes(app)
    return app


def _sse_event(event_type: str, payload: dict) -> str:
    return f"data: {json.dumps({'type': event_type, **payload}, ensure_ascii=False)}\n\n"


def _sources_from_chunks(chunks) -> list[SourceOut]:
    sources: list[SourceOut] = []
    seen: set[tuple[int, int]] = set()
    for chunk in chunks:
        key = (chunk.document_id, chunk.page)
        if key in seen:
            continue
        seen.add(key)
        sources.append(SourceOut(documentId=chunk.document_id, filename=chunk.filename, page=chunk.page))
        if len(sources) >= 5:
            break
    return sources


def _register_routes(app: FastAPI) -> None:
    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(status="ok")

    @app.post("/index", response_model=IndexResponse)
    def index(request: IndexRequest) -> IndexResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")
        try:
            indexed = indexing.index_document(
                app.state.collection, app.state.openai, app.state.settings, request
            )
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Indexing failed: {exc}") from exc

        app.state.corpus.upsert_document(
            request.documentId,
            [
                CorpusChunk(
                    document_id=request.documentId,
                    chunk_index=chunk.chunkIndex,
                    page=chunk.page,
                    filename=request.filename,
                    text=chunk.text,
                )
                for chunk in request.chunks
            ],
        )
        return IndexResponse(documentId=request.documentId, indexedChunks=indexed)

    @app.delete("/index/{document_id}", response_model=DeleteIndexResponse)
    def delete_index(document_id: int) -> DeleteIndexResponse:
        deleted = indexing.delete_document(app.state.collection, document_id)
        app.state.corpus.remove_document(document_id)
        return DeleteIndexResponse(documentId=document_id, deleted=deleted)

    @app.post("/query", response_model=QueryResponse)
    def query(request: QueryRequest) -> QueryResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")
        try:
            settings = app.state.settings
            candidate_limit = settings.top_k * 2 if settings.rerank_enabled else settings.top_k
            chunks = retrieval.search_chunks(
                app.state.collection,
                app.state.openai,
                settings,
                request.question,
                request.documentIds,
                candidate_limit,
                corpus=app.state.corpus,
            )
            chunks = rerank.rerank_chunks(
                app.state.openai, settings, request.question, chunks
            )[: settings.top_k]
            answer = generation.generate_answer(
                app.state.openai,
                app.state.settings,
                request.question,
                chunks,
                request.history,
            )
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Query failed: {exc}") from exc

        return QueryResponse(answer=answer, sources=_sources_from_chunks(chunks))

    @app.post("/query_stream")
    def query_stream(request: QueryRequest) -> StreamingResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")

        settings = app.state.settings
        collection = app.state.collection
        openai_client = app.state.openai
        corpus = app.state.corpus

        def event_stream():
            try:
                candidate_limit = settings.top_k * 2 if settings.rerank_enabled else settings.top_k
                chunks = retrieval.search_chunks(
                    collection,
                    openai_client,
                    settings,
                    request.question,
                    request.documentIds,
                    candidate_limit,
                    corpus=corpus,
                )
                chunks = rerank.rerank_chunks(
                    openai_client, settings, request.question, chunks
                )[: settings.top_k]
                parts: list[str] = []
                for delta in generation.stream_answer(
                    openai_client, settings, request.question, chunks, request.history
                ):
                    parts.append(delta)
                    yield _sse_event("token", {"content": delta})
                answer = "".join(parts).strip()
                if not answer:
                    answer = generation.NO_CONTEXT_ANSWER
                    yield _sse_event("token", {"content": answer})
                sources = _sources_from_chunks(chunks)
                yield _sse_event(
                    "done",
                    {"answer": answer, "sources": [source.model_dump() for source in sources]},
                )
            except Exception as exc:
                yield _sse_event("error", {"detail": f"Query failed: {exc}"})

        return StreamingResponse(event_stream(), media_type="text/event-stream")

    @app.post("/generate", response_model=GenerateResponse)
    def generate(request: GenerateRequest) -> GenerateResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")
        try:
            answer = generation.generate_from_context(
                app.state.openai,
                app.state.settings,
                request.question,
                request.context,
                request.history,
            )
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Generation failed: {exc}") from exc
        return GenerateResponse(answer=answer)

    @app.post("/generate_stream")
    def generate_stream(request: GenerateRequest) -> StreamingResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")

        settings = app.state.settings
        openai_client = app.state.openai

        def event_stream():
            try:
                parts: list[str] = []
                for delta in generation.stream_from_context(
                    openai_client, settings, request.question, request.context, request.history
                ):
                    parts.append(delta)
                    yield _sse_event("token", {"content": delta})
                answer = "".join(parts).strip() or generation.NO_WEB_ANSWER
                yield _sse_event("done", {"answer": answer})
            except Exception as exc:
                yield _sse_event("error", {"detail": f"Generation failed: {exc}"})

        return StreamingResponse(event_stream(), media_type="text/event-stream")

    @app.post("/complete", response_model=CompleteResponse)
    def complete(request: CompleteRequest) -> CompleteResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")
        try:
            answer = generation.complete(
                app.state.openai,
                app.state.settings,
                request.system,
                request.prompt,
                request.max_tokens,
            )
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Completion failed: {exc}") from exc
        return CompleteResponse(answer=answer)

    @app.post("/search", response_model=SearchResponse)
    def search(request: SearchRequest) -> SearchResponse:
        if app.state.openai is None:
            raise HTTPException(status_code=503, detail="LLM API key is not configured")
        try:
            chunks = retrieval.search_chunks(
                app.state.collection,
                app.state.openai,
                app.state.settings,
                request.query,
                request.documentIds,
                request.limit,
                corpus=app.state.corpus,
            )
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Search failed: {exc}") from exc

        return SearchResponse(
            results=[
                SearchResult(
                    documentId=chunk.document_id,
                    filename=chunk.filename,
                    page=chunk.page,
                    chunkIndex=chunk.chunk_index,
                    text=chunk.text,
                    score=chunk.score,
                )
                for chunk in chunks
            ]
        )


app = create_app()
