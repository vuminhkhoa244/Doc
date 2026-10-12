from pydantic_settings import BaseSettings, SettingsConfigDict

OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    llm_provider: str = "openrouter"
    openrouter_api_key: str = ""
    openrouter_base_url: str = OPENROUTER_BASE_URL
    openai_api_key: str = ""
    openai_base_url: str = ""
    embedding_model: str = "openai/text-embedding-3-small"
    chat_model: str = "dots-studio/dots-3-note-preview:free"
    chroma_persist_dir: str = "./data/chroma"
    collection_name: str = "documents"
    top_k: int = 5
    history_max_turns: int = 4
    embedding_batch_size: int = 100
    request_timeout_seconds: float = 60.0
    max_answer_tokens: int = 800
    max_context_chars: int = 4000
    hybrid_pool: int = 20
    rrf_k: int = 60
    rerank_enabled: bool = True
    rerank_max_tokens: int = 160

    @property
    def resolved_provider(self) -> str:
        provider = (self.llm_provider or "").strip().lower()
        if provider in {"", "auto"}:
            return "openrouter" if self.openrouter_api_key else "openai"
        return provider

    @property
    def resolved_api_key(self) -> str:
        if self.resolved_provider == "openrouter":
            return self.openrouter_api_key
        return self.openai_api_key

    @property
    def resolved_base_url(self) -> str | None:
        if self.resolved_provider == "openrouter":
            return self.openrouter_base_url
        return self.openai_base_url or None
