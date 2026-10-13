import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient

from test_api import FakeOpenAI, index_sample_document
from app.config import Settings
from app.corpus import Bm25Corpus, CorpusChunk
from app.main import create_app
from app.rerank import parse_rerank_order


class RerankFlipOpenAI(FakeOpenAI):
    """Returns a reversed order for rerank prompts, normal answers otherwise."""

    def _create_chat(self, model, messages, temperature=None, max_tokens=None, **kwargs):
        if messages and messages[0]["role"] == "system" and "rank search passages" in messages[0]["content"].lower():
            return _chat_reply("[2, 1]")
        return super()._create_chat(model, messages, temperature=temperature, max_tokens=max_tokens, **kwargs)


class RerankGarbageOpenAI(FakeOpenAI):
    """Returns unusable text for rerank prompts to exercise the fallback."""

    def _create_chat(self, model, messages, temperature=None, max_tokens=None, **kwargs):
        if messages and messages[0]["role"] == "system" and "rank search passages" in messages[0]["content"].lower():
            return _chat_reply("### not a json array ###")
        return super()._create_chat(model, messages, temperature=temperature, max_tokens=max_tokens, **kwargs)


def _chat_reply(content: str):
    from types import SimpleNamespace

    return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=content))])


def test_bm25_corpus_ranks_lexical_match():
    corpus = Bm25Corpus()
    texts = [
        "Employees receive 15 days of annual leave after probation.",
        "Unused leave may be carried over with manager approval.",
        "Sick leave requires a medical certificate.",
        "The quantum computing initiative launches next quarter.",
        "Remote work is allowed up to two days per week.",
    ]
    corpus.upsert_document(
        1,
        [
            CorpusChunk(document_id=1, chunk_index=index, page=index + 1, filename="handbook.pdf", text=text)
            for index, text in enumerate(texts)
        ],
    )

    hits = corpus.search("quantum", None, 3)
    assert hits
    assert hits[0].chunk_index == 3

    corpus.remove_document(1)
    assert corpus.search("quantum", None, 3) == []


def test_parse_rerank_order_handles_garbage_and_missing():
    assert parse_rerank_order("[3, 1, 2]", 3) == [2, 0, 1]
    assert parse_rerank_order("nonsense", 2) == [0, 1]
    assert parse_rerank_order("[9, 9, 1]", 2) == [0, 1]
    assert parse_rerank_order("[2]", 3) == [1, 0, 2]
    assert parse_rerank_order("", 1) == [0]


def test_bm25_warm_from_chroma_after_restart(tmp_path):
    persist_dir = str(tmp_path / "chroma")
    settings = Settings(openai_api_key="test-key", chroma_persist_dir=persist_dir)

    app = create_app(settings=settings, openai_client=FakeOpenAI())
    with TestClient(app) as client:
        index_sample_document(client)

    restarted = create_app(settings=settings, openai_client=FakeOpenAI())
    with TestClient(restarted) as client:
        assert len(restarted.state.corpus) == 2
        results = client.post("/search", json={"query": "annual leave", "limit": 5}).json()["results"]
        assert results
        assert all(result["documentId"] == 1 for result in results)


def test_query_rerank_reorders_sources(tmp_path):
    settings = Settings(openai_api_key="test-key", chroma_persist_dir=str(tmp_path / "chroma"))
    app = create_app(settings=settings, openai_client=RerankFlipOpenAI())
    with TestClient(app) as client:
        index_sample_document(client)

        baseline = client.post("/search", json={"query": "annual leave", "limit": 5}).json()["results"]
        assert baseline[0]["page"] == 12

        response = client.post(
            "/query",
            json={"question": "How many days of annual leave do employees get?"},
        )
        assert response.status_code == 200
        assert response.json()["sources"][0]["page"] == 13


def test_query_rerank_garbage_falls_back_to_retrieval_order(tmp_path):
    settings = Settings(openai_api_key="test-key", chroma_persist_dir=str(tmp_path / "chroma"))
    app = create_app(settings=settings, openai_client=RerankGarbageOpenAI())
    with TestClient(app) as client:
        index_sample_document(client)

        response = client.post(
            "/query",
            json={"question": "How many days of annual leave do employees get?"},
        )
        assert response.status_code == 200
        assert response.json()["sources"][0]["page"] == 12


def test_query_rerank_disabled_keeps_retrieval_order(tmp_path):
    settings = Settings(
        openai_api_key="test-key",
        chroma_persist_dir=str(tmp_path / "chroma"),
        rerank_enabled=False,
    )
    app = create_app(settings=settings, openai_client=FakeOpenAI())
    with TestClient(app) as client:
        index_sample_document(client)
        response = client.post(
            "/query",
            json={"question": "How many days of annual leave do employees get?"},
        )
        assert response.status_code == 200
        assert response.json()["sources"][0]["page"] == 12
