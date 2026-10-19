"""Integration tests for PgVectorStore against a real Postgres with pgvector.

Skipped unless TEST_DATABASE_URL is set, e.g.:

    TEST_DATABASE_URL=postgresql://user:pass@host/db?sslmode=require \
        python -m pytest tests/test_pgvector_store.py -q

Each test runs inside an isolated throwaway schema so it never touches the
real ``ai_service`` schema and parallel runs cannot collide.
"""

from __future__ import annotations

import os
import uuid

import psycopg
import pytest
from psycopg import sql

from app.store import ChunkInput, PgVectorStore

TEST_DATABASE_URL = os.getenv("TEST_DATABASE_URL", "")

pytestmark = pytest.mark.skipif(
    not TEST_DATABASE_URL,
    reason="TEST_DATABASE_URL not set; skipping pgvector integration tests",
)

# Small, hand-checkable dimension keeps the cosine math obvious in assertions.
DIM = 4


def _drop_schema(schema: str) -> None:
    with psycopg.connect(TEST_DATABASE_URL) as conn:
        with conn.cursor() as cur:
            cur.execute(
                sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema))
            )
        conn.commit()


@pytest.fixture()
def store():
    schema = f"test_{uuid.uuid4().hex[:12]}"
    store = PgVectorStore(conninfo=TEST_DATABASE_URL, schema=schema, vector_dim=DIM)
    store.open()
    try:
        yield store, schema
    finally:
        _drop_schema(schema)
        store.close()


def _seed(store, document_id: int = 1, filename: str = "doc.pdf") -> None:
    store.upsert(
        document_id=document_id,
        filename=filename,
        content_type="application/pdf",
        chunks=[
            ChunkInput(
                chunk_index=0,
                page=1,
                text="Cats are small domesticated animals.",
                embedding=[1.0, 0.0, 0.0, 0.0],
            ),
            ChunkInput(
                chunk_index=1,
                page=2,
                text="Dogs are loyal companions.",
                embedding=[0.0, 1.0, 0.0, 0.0],
            ),
        ],
    )


def test_upsert_is_idempotent_and_replaces_in_place(store):
    store, _schema = store
    _seed(store)
    assert store.count() == 2

    # Re-index the same document: chunk 0 is replaced, chunk 1 is untouched.
    store.upsert(
        document_id=1,
        filename="doc.pdf",
        content_type="application/pdf",
        chunks=[
            ChunkInput(
                chunk_index=0,
                page=1,
                text="Cats are small domesticated animals (updated).",
                embedding=[0.9, 0.1, 0.0, 0.0],
            ),
        ],
    )
    assert store.count() == 2
    rows = store.all_chunks()
    assert rows[0].text.endswith("(updated).")
    assert rows[1].text == "Dogs are loyal companions."


def test_query_orders_by_cosine_distance(store):
    store, _schema = store
    _seed(store)

    rows = store.query([1.0, 0.0, 0.0, 0.0], k=2)
    assert [row.chunk_index for row in rows] == [0, 1]
    assert rows[0].distance == pytest.approx(0.0, abs=1e-5)
    assert rows[1].distance == pytest.approx(1.0, abs=1e-5)


def test_query_respects_document_ids_filter(store):
    store, _schema = store
    _seed(store, document_id=1, filename="a.pdf")
    _seed(store, document_id=2, filename="b.pdf")

    rows = store.query([1.0, 0.0, 0.0, 0.0], k=10, document_ids=[2])
    assert rows
    assert all(row.document_id == 2 for row in rows)


def test_delete_document_reports_whether_anything_was_removed(store):
    store, _schema = store
    _seed(store)

    assert store.delete_document(1) is True
    assert store.delete_document(1) is False
    assert store.count() == 0


def test_all_chunks_orders_by_document_and_index(store):
    store, _schema = store
    _seed(store, document_id=1, filename="a.pdf")
    _seed(store, document_id=2, filename="b.pdf")

    rows = store.all_chunks()
    assert [(row.document_id, row.chunk_index) for row in rows] == [
        (1, 0),
        (1, 1),
        (2, 0),
        (2, 1),
    ]


def test_data_survives_store_restart(store):
    store, schema = store
    _seed(store)
    store.close()

    reopened = PgVectorStore(conninfo=TEST_DATABASE_URL, schema=schema, vector_dim=DIM)
    reopened.open()
    try:
        assert reopened.count() == 2
        rows = reopened.query([1.0, 0.0, 0.0, 0.0], k=1)
        assert rows[0].chunk_index == 0
    finally:
        reopened.close()


def test_dimension_mismatch_fails_fast():
    schema = f"test_{uuid.uuid4().hex[:12]}"
    store = PgVectorStore(conninfo=TEST_DATABASE_URL, schema=schema, vector_dim=DIM)
    store.open()
    try:
        wrong = PgVectorStore(conninfo=TEST_DATABASE_URL, schema=schema, vector_dim=DIM + 4)
        with pytest.raises(RuntimeError, match="dimension mismatch"):
            wrong.open()
    finally:
        _drop_schema(schema)
        store.close()
