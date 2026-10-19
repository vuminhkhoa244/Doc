from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Optional, Protocol, runtime_checkable

from psycopg import sql
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool


@dataclass(frozen=True)
class ChunkRow:
    """One stored chunk, as returned by the vector store."""

    document_id: int
    chunk_index: int
    page: int
    filename: str
    text: str
    distance: float = 0.0


@dataclass(frozen=True)
class ChunkInput:
    """One chunk handed to the store at index time, already embedded."""

    chunk_index: int
    page: int
    text: str
    embedding: list[float]


@runtime_checkable
class VectorStore(Protocol):
    """Storage backend for embedded document chunks.

    Implementations must be safe to call from FastAPI's threadpool. ``query``
    returns rows ordered best-first, where ``distance`` is cosine distance
    (lower is better); callers convert it to a similarity score.
    """

    def open(self) -> None:
        """Acquire resources (connections, indexes). Idempotent."""

    def close(self) -> None:
        """Release resources. Idempotent."""

    def ping(self) -> None:
        """Raise if the backend is unreachable."""

    def upsert(
        self,
        document_id: int,
        filename: str,
        content_type: str,
        chunks: list[ChunkInput],
    ) -> None:
        """Insert or replace every chunk of one document."""

    def delete_document(self, document_id: int) -> bool:
        """Remove all chunks of a document. Returns False if none existed."""

    def query(
        self,
        query_embedding: list[float],
        k: int,
        document_ids: Optional[list[int]] = None,
    ) -> list[ChunkRow]:
        """Return the k nearest chunks, optionally restricted to document_ids."""

    def all_chunks(self) -> list[ChunkRow]:
        """Return every stored chunk, ordered by (document_id, chunk_index)."""

    def count(self) -> int:
        """Return the total number of stored chunks."""


class PgVectorStore:
    """Postgres + pgvector-backed store, sharing the app database via a schema."""

    def __init__(self, conninfo: str, schema: str = "ai_service", vector_dim: int = 1024):
        self._conninfo = conninfo
        self._schema = schema
        self._vector_dim = int(vector_dim)
        self._pool: ConnectionPool | None = None

    def open(self) -> None:
        if self._pool is not None:
            return
        self._pool = ConnectionPool(
            conninfo=self._conninfo,
            open=False,
            kwargs={"row_factory": dict_row},
        )
        self._pool.open()
        self._ensure_schema()
        self._verify_dimension()

    def close(self) -> None:
        if self._pool is not None:
            self._pool.close()
            self._pool = None

    def ping(self) -> None:
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()

    def upsert(
        self,
        document_id: int,
        filename: str,
        content_type: str,
        chunks: list[ChunkInput],
    ) -> None:
        if not chunks:
            return
        table = self._table()
        values = sql.SQL(", ").join(
            sql.SQL("(%s, %s, %s, %s, %s, %s, %s::vector)") for _ in chunks
        )
        statement = sql.SQL(
            """
            INSERT INTO {} (document_id, chunk_index, filename, content_type, page, text, embedding)
            VALUES {}
            ON CONFLICT (document_id, chunk_index) DO UPDATE SET
                filename = EXCLUDED.filename,
                content_type = EXCLUDED.content_type,
                page = EXCLUDED.page,
                text = EXCLUDED.text,
                embedding = EXCLUDED.embedding
            """
        ).format(table, values)

        params: list[object] = []
        for chunk in chunks:
            params.extend(
                [
                    document_id,
                    chunk.chunk_index,
                    filename,
                    content_type,
                    chunk.page,
                    chunk.text,
                    json.dumps(chunk.embedding),
                ]
            )
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(statement, params)
            conn.commit()

    def delete_document(self, document_id: int) -> bool:
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    sql.SQL("DELETE FROM {} WHERE document_id = %s").format(self._table()),
                    (document_id,),
                )
                deleted = cur.rowcount > 0
            conn.commit()
        return deleted

    def query(
        self,
        query_embedding: list[float],
        k: int,
        document_ids: Optional[list[int]] = None,
    ) -> list[ChunkRow]:
        if k <= 0:
            return []
        table = self._table()
        # json.dumps yields the "[0.1, 0.2, ...]" text form pgvector parses.
        payload = json.dumps(query_embedding)
        if document_ids is None:
            statement = sql.SQL(
                """
                SELECT document_id, chunk_index, page, filename, text,
                       embedding <=> %s::vector AS distance
                FROM {}
                ORDER BY embedding <=> %s::vector
                LIMIT %s
                """
            ).format(table)
            params: list[object] = [payload, payload, k]
        else:
            statement = sql.SQL(
                """
                SELECT document_id, chunk_index, page, filename, text,
                       embedding <=> %s::vector AS distance
                FROM {}
                WHERE document_id = ANY(%s)
                ORDER BY embedding <=> %s::vector
                LIMIT %s
                """
            ).format(table)
            params = [payload, list(document_ids), payload, k]

        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(statement, params)
                rows = cur.fetchall()
        return [self._to_row(row) for row in rows]

    def all_chunks(self) -> list[ChunkRow]:
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    sql.SQL(
                        "SELECT document_id, chunk_index, page, filename, text "
                        "FROM {} ORDER BY document_id, chunk_index"
                    ).format(self._table())
                )
                rows = cur.fetchall()
        return [self._to_row(row) for row in rows]

    def count(self) -> int:
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(sql.SQL("SELECT COUNT(*) AS n FROM {}").format(self._table()))
                row = cur.fetchone()
        return int(row["n"])

    def _connection(self):
        if self._pool is None:
            raise RuntimeError("PgVectorStore is not open; call open() first")
        return self._pool.connection()

    def _table(self) -> sql.Composed:
        return sql.SQL("{}.chunks").format(sql.Identifier(self._schema))

    @staticmethod
    def _to_row(row: dict) -> ChunkRow:
        return ChunkRow(
            document_id=int(row["document_id"]),
            chunk_index=int(row["chunk_index"]),
            page=int(row["page"]),
            filename=str(row["filename"]),
            text=str(row["text"]),
            distance=float(row["distance"]) if row.get("distance") is not None else 0.0,
        )

    def _ensure_schema(self) -> None:
        table = self._table()
        dim = sql.SQL(str(self._vector_dim))
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(sql.SQL("CREATE EXTENSION IF NOT EXISTS vector"))
                cur.execute(
                    sql.SQL("CREATE SCHEMA IF NOT EXISTS {}").format(
                        sql.Identifier(self._schema)
                    )
                )
                cur.execute(
                    sql.SQL(
                        """
                        CREATE TABLE IF NOT EXISTS {} (
                            document_id  bigint      NOT NULL,
                            chunk_index  integer     NOT NULL,
                            filename     text        NOT NULL,
                            content_type text        NOT NULL,
                            page         integer     NOT NULL,
                            text         text        NOT NULL,
                            embedding    vector({}) NOT NULL,
                            PRIMARY KEY (document_id, chunk_index)
                        )
                        """
                    ).format(table, dim)
                )
                cur.execute(
                    sql.SQL(
                        "CREATE INDEX IF NOT EXISTS chunks_document_id_idx ON {} (document_id)"
                    ).format(table)
                )
                cur.execute(
                    sql.SQL(
                        """
                        CREATE INDEX IF NOT EXISTS chunks_hnsw_idx
                            ON {} USING hnsw (embedding vector_cosine_ops)
                        """
                    ).format(table)
                )
            conn.commit()

    def _verify_dimension(self) -> None:
        """Fail fast if the live column disagrees with VECTOR_DIM.

        pgvector reports the declared dimension in ``atttypmod``. A mismatch
        means EMBEDDING_MODEL changed under an existing table; without this
        check the first upload would fail with a cryptic SQL error.
        """
        with self._connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    sql.SQL(
                        """
                        SELECT a.atttypmod AS dim
                        FROM pg_attribute a
                        JOIN pg_class c ON a.attrelid = c.oid
                        JOIN pg_namespace n ON c.relnamespace = n.oid
                        WHERE n.nspname = %s AND c.relname = 'chunks'
                          AND a.attname = 'embedding'
                          AND a.attnum > 0 AND NOT a.attisdropped
                        """
                    ),
                    (self._schema,),
                )
                row = cur.fetchone()
        if row is None:
            raise RuntimeError(
                f"Embedding column not found in {self._schema}.chunks after schema creation"
            )
        actual = int(row["dim"])
        if actual != self._vector_dim:
            raise RuntimeError(
                f"Embedding dimension mismatch: {self._schema}.chunks.embedding is "
                f"vector({actual}) but the service is configured for {self._vector_dim} "
                f"dimensions. Recreate the chunks table or set VECTOR_DIM to {actual}."
            )
