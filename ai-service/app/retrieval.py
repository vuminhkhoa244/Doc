from dataclasses import dataclass
from typing import Optional

from app.config import Settings
from app.corpus import Bm25Corpus


@dataclass
class RetrievedChunk:
    document_id: int
    filename: str
    page: int
    chunk_index: int
    text: str
    score: float


def embed_query(openai_client, settings: Settings, query: str) -> list[float]:
    response = openai_client.embeddings.create(model=settings.embedding_model, input=[query])
    return response.data[0].embedding


def _vector_search(
    collection,
    openai_client,
    settings: Settings,
    query: str,
    document_ids: Optional[list[int]],
    pool: int,
) -> list[RetrievedChunk]:
    where = {"documentId": {"$in": document_ids}} if document_ids is not None else None
    query_embedding = embed_query(openai_client, settings, query)

    result = collection.query(
        query_embeddings=[query_embedding],
        n_results=min(pool, collection.count()),
        where=where,
        include=["documents", "metadatas", "distances"],
    )

    chunks: list[RetrievedChunk] = []
    ids = result.get("ids") or [[]]
    documents = result.get("documents") or [[]]
    metadatas = result.get("metadatas") or [[]]
    distances = result.get("distances") or [[]]

    for _id, text, metadata, distance in zip(ids[0], documents[0], metadatas[0], distances[0]):
        chunks.append(
            RetrievedChunk(
                document_id=int(metadata["documentId"]),
                filename=str(metadata["filename"]),
                page=int(metadata["page"]),
                chunk_index=int(metadata["chunkIndex"]),
                text=text or "",
                score=round(1.0 - float(distance), 6),
            )
        )
    return chunks


def _bm25_search(
    corpus: Bm25Corpus,
    query: str,
    document_ids: Optional[list[int]],
    pool: int,
) -> list[RetrievedChunk]:
    chunks: list[RetrievedChunk] = []
    for hit in corpus.search(query, document_ids, pool):
        stored = corpus.get(hit.document_id, hit.chunk_index)
        if stored is None:
            continue
        chunks.append(
            RetrievedChunk(
                document_id=stored.document_id,
                filename=stored.filename,
                page=stored.page,
                chunk_index=stored.chunk_index,
                text=stored.text,
                score=round(hit.score, 6),
            )
        )
    return chunks


def _fuse(
    ranked_lists: list[list[RetrievedChunk]],
    rrf_k: int,
    limit: int,
) -> list[RetrievedChunk]:
    """Reciprocal Rank Fusion over ranked candidate lists.

    Each list is already sorted best-first; a chunk's fused score is
    sum of 1 / (rrf_k + rank) across the lists it appears in. Scores are
    normalized so the best chunk scores 1.0.
    """
    scores: dict[tuple[int, int], float] = {}
    first_seen: dict[tuple[int, int], RetrievedChunk] = {}

    for ranked in ranked_lists:
        for rank, chunk in enumerate(ranked, start=1):
            key = (chunk.document_id, chunk.chunk_index)
            scores[key] = scores.get(key, 0.0) + 1.0 / (rrf_k + rank)
            first_seen.setdefault(key, chunk)

    ordered = sorted(scores, key=lambda key: scores[key], reverse=True)[:limit]
    if not ordered:
        return []
    best = scores[ordered[0]]
    return [
        RetrievedChunk(
            document_id=first_seen[key].document_id,
            filename=first_seen[key].filename,
            page=first_seen[key].page,
            chunk_index=first_seen[key].chunk_index,
            text=first_seen[key].text,
            score=round(scores[key] / best, 6),
        )
        for key in ordered
    ]


def search_chunks(
    collection,
    openai_client,
    settings: Settings,
    query: str,
    document_ids: Optional[list[int]],
    limit: int,
    corpus: Optional[Bm25Corpus] = None,
) -> list[RetrievedChunk]:
    if document_ids is not None and len(document_ids) == 0:
        return []

    vector_count = collection.count()
    corpus_size = len(corpus) if corpus is not None else 0
    if vector_count == 0 and corpus_size == 0:
        return []

    pool = max(limit, settings.hybrid_pool)

    vector_candidates: list[RetrievedChunk] = []
    if vector_count > 0:
        try:
            vector_candidates = _vector_search(
                collection, openai_client, settings, query, document_ids, pool
            )
        except Exception:
            # Vector side is best-effort; BM25 still provides lexical recall.
            vector_candidates = []

    bm25_candidates: list[RetrievedChunk] = []
    if corpus is not None and corpus_size > 0:
        bm25_candidates = _bm25_search(corpus, query, document_ids, pool)

    if not vector_candidates:
        return bm25_candidates[:limit]
    if not bm25_candidates:
        return vector_candidates[:limit]

    return _fuse([vector_candidates, bm25_candidates], settings.rrf_k, limit)
