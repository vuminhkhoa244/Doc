from pydantic_settings import BaseSettings, SettingsConfigDict

OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    llm_provider: str = "openrouter"
    openrouter_api_key: str = ""
    openrouter_base_url: str = OPENROUTER_BASE_URL
    openai_api_key: str = ""
    openai_base_url: str = ""
    embedding_model: str = "nvidia/nemotron-3-embed-1b:free"
    chat_model: str = "dots-studio/dots-3-note-preview:free"
    # Postgres DSN with the pgvector extension available (e.g. Neon).
    database_url: str = ""
    # Schema inside that database owned by this service. Keeps Hibernate out
    # of the vector table's way when the app database is shared with the backend.
    db_schema: str = "ai_service"
    # Must match the dimension of EMBEDDING_MODEL. Changing the model requires
    # recreating the chunks table; startup fails loudly on a mismatch.
    vector_dim: int = 1024
    top_k: int = 5
    history_max_turns: int = 4
    embedding_batch_size: int = 100
    request_timeout_seconds: float = 90.0
    max_answer_tokens: int = 4000
    max_context_chars: int = 4000
    hybrid_pool: int = 20
    rrf_k: int = 60
    rerank_enabled: bool = True
    rerank_max_tokens: int = 1000
    chat_reasoning_enabled: bool = False

    @property
    def resolved_provider(self) -> str:
        provider = (self.llm_provider or "").strip().lower()
        if provider in {"", "auto"}:
            return "openrouter" if self.openrouter_api_key else "openai"
        return provider

    def reasoning_extra(self) -> dict:
        """OpenRouter request override controlling provider-side thinking.

        When False, asks the provider to skip reasoning tokens so answers start
        streaming immediately. Ignored by non-OpenRouter providers.
        """
        if self.resolved_provider != "openrouter":
            return {}
        return {"extra_body": {"reasoning": {"enabled": self.chat_reasoning_enabled}}}

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
