from app.config import Settings
from app.models import IndexRequest
from app.store import ChunkInput, VectorStore


def _embed_texts(openai_client, settings: Settings, texts: list[str]) -> list[list[float]]:
    embeddings: list[list[float]] = []
    batch_size = settings.embedding_batch_size
    for start in range(0, len(texts), batch_size):
        batch = texts[start : start + batch_size]
        response = openai_client.embeddings.create(model=settings.embedding_model, input=batch)
        embeddings.extend(item.embedding for item in response.data)
    return embeddings


def index_document(store: VectorStore, openai_client, settings: Settings, request: IndexRequest) -> int:
    if not request.chunks:
        return 0

    texts = [chunk.text for chunk in request.chunks]
    embeddings = _embed_texts(openai_client, settings, texts)

    store.upsert(
        document_id=request.documentId,
        filename=request.filename,
        content_type=request.contentType,
        chunks=[
            ChunkInput(
                chunk_index=chunk.chunkIndex,
                page=chunk.page,
                text=chunk.text,
                embedding=embedding,
            )
            for chunk, embedding in zip(request.chunks, embeddings)
        ],
    )
    return len(request.chunks)


def delete_document(store: VectorStore, document_id: int) -> bool:
    return store.delete_document(document_id)
