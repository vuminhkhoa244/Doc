"""Test doubles for the VectorStore seam.

``InMemoryVectorStore`` implements the same contract as ``PgVectorStore`` so
route tests can assert on real ranking behaviour instead of a stub that always
returns everything in insertion order.
"""

from __future__ import annotations

import math
from typing import Optional

from app.store import ChunkInput, ChunkRow, VectorStore


class InMemoryVectorStore:
    """Dict-backed VectorStore with real cosine distance.

    Rows are grouped per document so ``upsert``/``delete_document`` mirror the
    SQL store's per-document semantics. ``distance`` is ``1 - cosine_similarity``,
    matching what pgvector's ``<=>`` operator returns, so callers can keep the
    existing "convert distance to score" logic unchanged.
    """

    def __init__(self, vector_dim: int = 8):
        self._vector_dim = vector_dim
        self._docs: dict[int, dict[int, dict]] = {}
        self.opened = False
        self.closed = False

    def open(self) -> None:
        self.opened = True
        self.closed = False

    def close(self) -> None:
        # Data survives close, like a real database disconnect.
        self.closed = True

    def ping(self) -> None:
        if not self.opened or self.closed:
            raise RuntimeError("InMemoryVectorStore is not open")

    def upsert(
        self,
        document_id: int,
        filename: str,
        content_type: str,
        chunks: list[ChunkInput],
    ) -> None:
        doc = self._docs.setdefault(document_id, {})
        for chunk in chunks:
            doc[chunk.chunk_index] = {
                "filename": filename,
                "content_type": content_type,
                "page": chunk.page,
                "text": chunk.text,
                "embedding": list(chunk.embedding),
            }

    def delete_document(self, document_id: int) -> bool:
        return self._docs.pop(document_id, None) is not None

    def query(
        self,
        query_embedding: list[float],
        k: int,
        document_ids: Optional[list[int]] = None,
    ) -> list[ChunkRow]:
        if k <= 0:
            return []
        rows: list[ChunkRow] = []
        for doc_id, doc in self._docs.items():
            if document_ids is not None and doc_id not in document_ids:
                continue
            for chunk_index, entry in doc.items():
                rows.append(
                    ChunkRow(
                        document_id=doc_id,
                        chunk_index=chunk_index,
                        page=entry["page"],
                        filename=entry["filename"],
                        text=entry["text"],
                        distance=_cosine_distance(query_embedding, entry["embedding"]),
                    )
                )
        rows.sort(key=lambda row: row.distance)
        return rows[:k]

    def all_chunks(self) -> list[ChunkRow]:
        rows: list[ChunkRow] = []
        for doc_id in sorted(self._docs):
            for chunk_index in sorted(self._docs[doc_id]):
                entry = self._docs[doc_id][chunk_index]
                rows.append(
                    ChunkRow(
                        document_id=doc_id,
                        chunk_index=chunk_index,
                        page=entry["page"],
                        filename=entry["filename"],
                        text=entry["text"],
                        distance=0.0,
                    )
                )
        return rows

    def count(self) -> int:
        return sum(len(doc) for doc in self._docs.values())


def _cosine_distance(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(x * x for x in b))
    if norm_a == 0.0 or norm_b == 0.0:
        return 1.0
    similarity = max(-1.0, min(1.0, dot / (norm_a * norm_b)))
    return 1.0 - similarity


# Static type checkers (and readers) can use this to confirm test doubles
# honour the production protocol.
_: type[VectorStore] = InMemoryVectorStore
