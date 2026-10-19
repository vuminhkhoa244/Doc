import re
import threading
from dataclasses import dataclass

from rank_bm25 import BM25Okapi

_TOKEN_PATTERN = re.compile(r"\w+")


def _tokenize(text: str) -> list[str]:
    return _TOKEN_PATTERN.findall(text.lower())


@dataclass(frozen=True)
class CorpusChunk:
    document_id: int
    chunk_index: int
    page: int
    filename: str
    text: str


@dataclass(frozen=True)
class Bm25Hit:
    document_id: int
    chunk_index: int
    score: float


class Bm25Corpus:
    """In-process BM25 index kept in sync with the vector store.

    The corpus is small (personal-document scale), so the index is rebuilt
    on each mutation instead of maintaining incremental structures.
    """

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._chunks: dict[int, list[CorpusChunk]] = {}
        self._bm25: BM25Okapi | None = None
        self._keys: list[tuple[int, int]] = []

    def __len__(self) -> int:
        return sum(len(chunks) for chunks in self._chunks.values())

    def upsert_document(self, document_id: int, chunks: list[CorpusChunk]) -> None:
        if not chunks:
            return
        with self._lock:
            self._chunks[document_id] = chunks
            self._rebuild()

    def remove_document(self, document_id: int) -> bool:
        with self._lock:
            if document_id not in self._chunks:
                return False
            del self._chunks[document_id]
            self._rebuild()
            return True

    def warm_from_store(self, store) -> None:
        """Load every indexed chunk from the vector store into the BM25 index."""
        rows = store.all_chunks()

        by_document: dict[int, list[CorpusChunk]] = {}
        for row in rows:
            by_document.setdefault(row.document_id, []).append(
                CorpusChunk(
                    document_id=row.document_id,
                    chunk_index=row.chunk_index,
                    page=row.page,
                    filename=row.filename,
                    text=row.text or "",
                )
            )

        with self._lock:
            self._chunks = by_document
            self._rebuild()

    def search(self, query: str, document_ids: list[int] | None, top_k: int) -> list[Bm25Hit]:
        if top_k <= 0:
            return []
        with self._lock:
            if self._bm25 is None or not self._keys:
                return []
            allowed = set(document_ids) if document_ids is not None else None
            scores = self._bm25.get_scores(_tokenize(query))

            hits: list[Bm25Hit] = []
            for position, score in enumerate(scores):
                if score <= 0:
                    continue
                key = self._keys[position]
                if allowed is not None and key[0] not in allowed:
                    continue
                hits.append(
                    Bm25Hit(document_id=key[0], chunk_index=key[1], score=float(score))
                )
            hits.sort(key=lambda hit: hit.score, reverse=True)
            return hits[:top_k]

    def get(self, document_id: int, chunk_index: int) -> CorpusChunk | None:
        with self._lock:
            for chunk in self._chunks.get(document_id, []):
                if chunk.chunk_index == chunk_index:
                    return chunk
            return None

    def _rebuild(self) -> None:
        self._keys = []
        texts: list[list[str]] = []
        for document_id in sorted(self._chunks):
            for chunk in sorted(self._chunks[document_id], key=lambda c: c.chunk_index):
                self._keys.append((document_id, chunk.chunk_index))
                texts.append(_tokenize(chunk.text))
        self._bm25 = BM25Okapi(texts) if texts else None
