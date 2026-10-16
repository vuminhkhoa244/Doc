import json

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from tests.test_api import FakeOpenAI


def index_document(client, document_id, filename, texts):
    response = client.post(
        "/index",
        json={
            "documentId": document_id,
            "filename": filename,
            "contentType": "text/plain",
            "chunks": [
                {"chunkIndex": index, "page": index + 1, "text": text}
                for index, text in enumerate(texts)
            ],
        },
    )
    assert response.status_code == 200


@pytest.fixture()
def client(tmp_path):
    settings = Settings(
        openai_api_key="test-key",
        chroma_persist_dir=str(tmp_path / "chroma"),
    )
    app = create_app(settings=settings, openai_client=FakeOpenAI())
    with TestClient(app) as test_client:
        yield test_client


def index_two_docs(client):
    index_document(
        client,
        document_id=1,
        filename="docker-notes.txt",
        texts=[
            "Containers share the host kernel.",
            "Docker compose starts the backend service.",
            "Volumes persist data across container restarts.",
            "Images are layered and cached for rebuilds.",
        ],
    )
    index_document(
        client,
        document_id=2,
        filename="handbook.pdf",
        texts=[
            "Employees receive 15 days of annual leave after probation.",
            "Unused leave may be carried over with manager approval.",
        ],
    )


def test_focus_miss_still_answers_from_library(client):
    index_two_docs(client)

    response = client.post(
        "/query",
        json={
            "question": "How many days of annual leave do employees get?",
            "documentIds": [1, 2],
            "focusDocumentIds": [1],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "annual leave" in body["answer"]
    assert any(source["documentId"] == 2 for source in body["sources"])


def test_focus_hit_answers_from_focused_doc(client):
    index_two_docs(client)

    response = client.post(
        "/query",
        json={
            "question": "How many days of annual leave do employees get?",
            "documentIds": [1, 2],
            "focusDocumentIds": [2],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "annual leave" in body["answer"]
    assert body["sources"]
    assert body["sources"][0]["documentId"] == 2


def test_focus_equal_to_universe_behaves_like_plain_query(client):
    index_two_docs(client)

    response = client.post(
        "/query",
        json={
            "question": "How many days of annual leave do employees get?",
            "documentIds": [1, 2],
            "focusDocumentIds": [1, 2],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "annual leave" in body["answer"]
    assert any(source["documentId"] == 2 for source in body["sources"])


def test_query_stream_focus_miss_still_answers_from_library(client):
    index_two_docs(client)
    with client.stream(
        "POST",
        "/query_stream",
        json={
            "question": "How many days of annual leave do employees get?",
            "documentIds": [1, 2],
            "focusDocumentIds": [1],
        },
    ) as response:
        assert response.status_code == 200
        raw = response.read().decode("utf-8")

    events = []
    for frame in raw.strip().split("\n\n"):
        for line in frame.split("\n"):
            if line.startswith("data:"):
                events.append(json.loads(line[len("data:") :].strip()))
    done = [event for event in events if event["type"] == "done"]
    assert len(done) == 1
    assert "annual leave" in done[0]["answer"]
    assert any(source["documentId"] == 2 for source in done[0]["sources"])


def test_hard_filter_still_applies_to_search_endpoint(client):
    index_two_docs(client)

    filtered = client.post(
        "/search",
        json={"query": "annual leave", "documentIds": [1], "limit": 10},
    ).json()["results"]
    assert filtered
    assert all(result["documentId"] == 1 for result in filtered)
