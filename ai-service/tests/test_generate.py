from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app import generation
from app.config import Settings
from app.main import create_app
from tests.fakes import InMemoryVectorStore

WEB_CONTEXT = (
    "[1] Docker overview\nURL: https://docs.docker.com\n"
    "Docker containers share the host operating system kernel.\n\n"
    "[2] Docker storage drivers\nURL: https://docs.docker.com/storage\n"
    "OverlayFS is the default storage driver on most Linux systems."
)


class CapturingOpenAI:
    def __init__(self, reply: str = "Docker containers share the host kernel."):
        self.reply = reply
        self.calls: list[list[dict]] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create_chat))

    def _create_chat(self, model, messages, temperature=None, max_tokens=None, **kwargs):
        self.calls.append(messages)
        message = SimpleNamespace(content=self.reply)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


@pytest.fixture()
def fake() -> CapturingOpenAI:
    return CapturingOpenAI()


@pytest.fixture()
def client(fake):
    settings = Settings(openai_api_key="test-key")
    app = create_app(settings=settings, store=InMemoryVectorStore(), openai_client=fake)
    with TestClient(app) as test_client:
        yield test_client


def test_generate_returns_grounded_answer(client, fake):
    response = client.post(
        "/generate",
        json={"question": "Do Docker containers have their own kernel?", "context": WEB_CONTEXT},
    )
    assert response.status_code == 200
    assert response.json()["answer"] == "Docker containers share the host kernel."

    messages = fake.calls[-1]
    assert "web search passages" in messages[0]["content"]
    last_user = messages[-1]["content"]
    assert last_user.startswith("Passages:")
    assert "https://docs.docker.com" in last_user
    assert last_user.endswith("Question: Do Docker containers have their own kernel?")


def test_generate_includes_history(client, fake):
    client.post(
        "/generate",
        json={
            "question": "And storage drivers?",
            "context": WEB_CONTEXT,
            "history": [{"question": "Kernel?", "answer": "Shared kernel."}],
        },
    )
    messages = fake.calls[-1]
    roles = [(m["role"], m["content"]) for m in messages[1:5]]
    assert ("user", "Kernel?") in roles
    assert ("assistant", "Shared kernel.") in roles


def test_generate_rejects_blank_context(client):
    response = client.post("/generate", json={"question": "hello", "context": ""})
    assert response.status_code == 422


def test_generate_rejects_blank_question(client):
    response = client.post("/generate", json={"question": "", "context": WEB_CONTEXT})
    assert response.status_code == 422


def test_generate_without_api_key_returns_503():
    settings = Settings(
        openrouter_api_key="",
        openai_api_key="",
    )
    app = create_app(settings=settings, store=InMemoryVectorStore(), openai_client=None)
    with TestClient(app) as test_client:
        response = test_client.post(
            "/generate",
            json={"question": "Anything?", "context": WEB_CONTEXT},
        )
    assert response.status_code == 503


def test_build_web_context_keeps_full_blocks_within_budget():
    context = "[1] first block\n\n[2] second block"
    result = generation.build_web_context(context, max_chars=len("[1] first block"))
    assert result == "[1] first block"


def test_build_web_context_truncates_oversized_single_block():
    huge = "x" * 500
    result = generation.build_web_context(huge, max_chars=100)
    assert len(result) == 100
