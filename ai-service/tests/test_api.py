import math
import zlib
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def fake_embedding(text: str) -> list[float]:
    words = text.lower().split()
    vector = [0.0] * 8
    for word in words:
        vector[zlib.crc32(word.encode("utf-8")) % 8] += 1.0
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]


class FakeOpenAI:
    def __init__(self):
        self.embeddings = SimpleNamespace(create=self._create_embeddings)
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create_chat))

    def _create_embeddings(self, model, input):
        data = [SimpleNamespace(embedding=fake_embedding(text)) for text in input]
        return SimpleNamespace(data=data)

    def _create_chat(self, model, messages, temperature=None, max_tokens=None):
        last_user = next(m for m in reversed(messages) if m["role"] == "user")
        has_context = "Context:" in last_user["content"] and "[1]" in last_user["content"]
        content = (
            "Employees receive 15 days of annual leave."
            if has_context
            else "I could not find a matching answer in your indexed documents."
        )
        message = SimpleNamespace(content=content)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


@pytest.fixture()
def client(tmp_path):
    settings = Settings(
        openai_api_key="test-key",
        chroma_persist_dir=str(tmp_path / "chroma"),
    )
    app = create_app(settings=settings, openai_client=FakeOpenAI())
    with TestClient(app) as test_client:
        yield test_client


def index_sample_document(client, document_id=1):
    response = client.post(
        "/index",
        json={
            "documentId": document_id,
            "filename": "Employee Handbook.pdf",
            "contentType": "application/pdf",
            "chunks": [
                {"chunkIndex": 0, "page": 12, "text": "Employees receive 15 days of annual leave after probation."},
                {"chunkIndex": 1, "page": 13, "text": "Unused leave may be carried over with manager approval."},
            ],
        },
    )
    assert response.status_code == 200
    return response.json()


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_index_and_query_returns_sources(client):
    indexed = index_sample_document(client)
    assert indexed == {"documentId": 1, "indexedChunks": 2}

    response = client.post(
        "/query",
        json={"question": "How many days of annual leave do employees get?"},
    )
    assert response.status_code == 200
    body = response.json()
    assert "annual leave" in body["answer"]
    assert body["sources"][0] == {"documentId": 1, "filename": "Employee Handbook.pdf", "page": 12}
    assert all(source["documentId"] == 1 for source in body["sources"])


def test_query_with_history(client):
    index_sample_document(client)
    response = client.post(
        "/query",
        json={
            "question": "Can unused leave roll over?",
            "history": [{"question": "How many days?", "answer": "Employees receive 15 days."}],
        },
    )
    assert response.status_code == 200


def test_query_no_documents_returns_fallback(client):
    response = client.post("/query", json={"question": "What is annual leave?"})
    assert response.status_code == 200
    body = response.json()
    assert body["sources"] == []
    assert "could not find" in body["answer"].lower()


def test_search_filters_by_document(client):
    index_sample_document(client, document_id=1)
    index_sample_document(client, document_id=2)

    all_results = client.post("/search", json={"query": "annual leave", "limit": 10}).json()["results"]
    assert len(all_results) >= 2

    filtered = client.post(
        "/search",
        json={"query": "annual leave", "documentIds": [2], "limit": 10},
    ).json()["results"]
    assert filtered
    assert all(result["documentId"] == 2 for result in filtered)


def test_delete_index(client):
    index_sample_document(client)
    response = client.delete("/index/1")
    assert response.status_code == 200
    assert response.json() == {"documentId": 1, "deleted": True}

    results = client.post("/search", json={"query": "annual leave"}).json()["results"]
    assert results == []


def test_query_validation_rejects_blank_question(client):
    response = client.post("/query", json={"question": ""})
    assert response.status_code == 422
