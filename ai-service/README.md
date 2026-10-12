# AI Service

FastAPI service for embeddings, vector retrieval, and LLM answer generation.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/index` | Embed and upsert document chunks into ChromaDB |
| DELETE | `/index/{documentId}` | Remove all vectors for a document |
| POST | `/query` | RAG query: hybrid retrieval → LLM rerank → generate an answer with sources |
| POST | `/search` | Hybrid semantic + lexical search over indexed chunks |
| GET | `/health` | Liveness probe |

## Retrieval pipeline

Queries run through a three-stage pipeline:

1. **Hybrid retrieval** — vector search (Chroma, cosine) and BM25 lexical
   search (in-process `rank-bm25` index kept in sync with Chroma) each
   produce a candidate pool, fused with **Reciprocal Rank Fusion** (RRF).
2. **LLM rerank** — the fused candidates are reordered by the chat model
   (cheap with free models; disable with `RERANK_ENABLED=false`). Any
   rerank failure falls back to retrieval order.
3. **Generation** — the top chunks are packed into the prompt for a
   grounded answer with `[n] (file, page)` citations.

The BM25 index is warmed from Chroma on startup, so it survives restarts.

## Configuration

Copy `.env.example` to `.env` and set `OPENROUTER_API_KEY` (free key from
https://openrouter.ai/keys). The service talks to OpenRouter's OpenAI-compatible
API for both chat and embeddings. Defaults are
`dots-studio/dots-3-note-preview:free` (chat, zero credits) and
`openai/text-embedding-3-small` (embeddings, ~$0.02/1M tokens).

To use OpenAI directly instead, set `LLM_PROVIDER=openai` and `OPENAI_API_KEY`.

## Deviations from spec

- **No LangChain**: chunking happens in the Spring Boot backend, so LangChain
  would add no value here. The service uses the OpenAI SDK and chromadb directly.
- **ChromaDB embedded**: Chroma runs in-process with a persistent local directory
  (`CHROMA_PERSIST_DIR`) instead of a standalone server. A dedicated Chroma
  container can replace it later without API changes.
- **Hybrid search + rerank** (Phase 4 items) are implemented instead of
  plain vector search, using RRF fusion and LLM-based reranking.

## Run locally

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt pytest httpx
uvicorn app.main:app --reload --port 8000
```

## Tests

Tests mock the OpenAI client, so no API key is required:

```bash
pytest tests -q
```
