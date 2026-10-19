# AI Service

FastAPI service for embeddings, vector retrieval, and LLM answer generation.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/index` | Embed and upsert document chunks into the vector store (Postgres/pgvector) |
| DELETE | `/index/{documentId}` | Remove all vectors for a document |
| POST | `/query` | RAG query: hybrid retrieval → LLM rerank → generate an answer with sources |
| POST | `/search` | Hybrid semantic + lexical search over indexed chunks |
| POST | `/generate` | Generate an answer from caller-provided context (used by the backend's web-search tool) |
| GET | `/health` | Readiness probe (pings the vector store; 503 if unreachable) |

## Retrieval pipeline

Queries run through a three-stage pipeline:

1. **Hybrid retrieval** — vector search (pgvector, cosine distance) and BM25
   lexical search (in-process `rank-bm25` index kept in sync with the store)
   each produce a candidate pool, fused with **Reciprocal Rank Fusion** (RRF).
2. **LLM rerank** — the fused candidates are reordered by the chat model
   (cheap with free models; disable with `RERANK_ENABLED=false`). Any
   rerank failure falls back to retrieval order.
3. **Generation** — the top chunks are packed into the prompt for a
   grounded answer with `[n] (file, page)` citations.

The BM25 index is warmed from the vector store on startup, so it survives
restarts.

`/generate` skips retrieval entirely: it takes a caller-provided context
string (e.g. web-search snippets from the backend's web-search client) and
drafts a grounded answer with a web-specific system prompt.

## Configuration

Copy `.env.example` to `.env` and set `OPENROUTER_API_KEY` (free key from
https://openrouter.ai/keys). The service talks to OpenRouter's OpenAI-compatible
API for both chat and embeddings. Defaults are
`dots-studio/dots-3-note-preview:free` (chat) and
`nvidia/nemotron-3-embed-1b:free` (embeddings) — both free tiers.

To use OpenAI directly instead, set `LLM_PROVIDER=openai` and `OPENAI_API_KEY`.

Vectors are stored in Postgres via pgvector. Set `DATABASE_URL` to a DSN with
the `vector` extension available (e.g. your Neon connection string); the
service creates its own schema (`DB_SCHEMA`, default `ai_service`) and table
on boot. `VECTOR_DIM` must match the embedding model (default `1024` for
Nemotron); startup fails loudly on a dimension mismatch.

## Deviations from spec

- **No LangChain**: chunking happens in the Spring Boot backend, so LangChain
  would add no value here. The service uses the OpenAI SDK directly.
- **Postgres/pgvector instead of a dedicated vector DB**: vectors live in a
  `chunks` table (HNSW, cosine) inside a dedicated schema, so the same
  database can back the Spring Boot app. A `VectorStore` protocol keeps the
  swap isolated — tests use an in-memory double with real cosine semantics.
- **Hybrid search + rerank** (Phase 4 items) are implemented instead of
  plain vector search, using RRF fusion and LLM-based reranking.

## Run locally

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt pytest httpx
# set DATABASE_URL in .env to a Postgres DSN with pgvector available
uvicorn app.main:app --reload --port 8000
```

## Tests

Tests mock the OpenAI client and use an in-memory vector store, so no API
key or database is required:

```bash
pytest tests -q               # 31 route/pipeline tests

# optional: run the pgvector store tests against a real database
TEST_DATABASE_URL=postgresql://user:pass@host/db?sslmode=require \
    pytest tests/test_pgvector_store.py -q
```
