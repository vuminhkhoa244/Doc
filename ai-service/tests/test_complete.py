from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


class CapturingOpenAI:
    def __init__(self, reply: str = '{"tool":"sql","reason":"counts"}'):
        self.reply = reply
        self.calls: list[list[dict]] = []
        self.kwargs: list[dict] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create_chat))

    def _create_chat(self, model, messages, temperature=None, max_tokens=None, **kwargs):
        self.calls.append(messages)
        self.kwargs.append({"temperature": temperature, "max_tokens": max_tokens, **kwargs})
        message = SimpleNamespace(content=self.reply)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


@pytest.fixture()
def fake() -> CapturingOpenAI:
    return CapturingOpenAI()


@pytest.fixture()
def client(tmp_path, fake):
    settings = Settings(
        openai_api_key="test-key",
        chroma_persist_dir=str(tmp_path / "chroma"),
    )
    app = create_app(settings=settings, openai_client=fake)
    with TestClient(app) as test_client:
        yield test_client


def test_complete_uses_caller_system_prompt(client, fake):
    response = client.post(
        "/complete",
        json={"system": "Reply with JSON only.", "prompt": "Route: How many employees?", "max_tokens": 64},
    )
    assert response.status_code == 200
    assert response.json()["answer"] == '{"tool":"sql","reason":"counts"}'

    messages = fake.calls[-1]
    assert messages[0] == {"role": "system", "content": "Reply with JSON only."}
    assert messages[-1] == {"role": "user", "content": "Route: How many employees?"}
    assert fake.kwargs[-1]["temperature"] == 0.0
    assert fake.kwargs[-1]["max_tokens"] == 64


def test_complete_rejects_blank_prompt(client):
    response = client.post("/complete", json={"system": "sys", "prompt": ""})
    assert response.status_code == 422


def test_complete_requires_api_key(tmp_path):
    settings = Settings(
        llm_provider="openai",
        openai_api_key="",
        openrouter_api_key="",
        chroma_persist_dir=str(tmp_path / "chroma"),
    )
    app = create_app(settings=settings)
    with TestClient(app) as test_client:
        response = test_client.post(
            "/complete", json={"system": "sys", "prompt": "hello"}
        )
        assert response.status_code == 503
