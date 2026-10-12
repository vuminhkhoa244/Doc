# AI Document Assistant

A full-stack RAG (retrieval-augmented generation) app. Upload documents (PDF,
TXT, DOCX), and ask questions in chat — the system retrieves the most relevant
passages from your documents and answers with grounded citations (file + page).

Three services:

| Service | Tech | Port | Role |
|---|---|---|---|
| `ai-service/` | Python + FastAPI + ChromaDB | 8000 | Embeddings, vector search, LLM answer generation (RAG) |
| `backend/` | Java + Spring Boot + H2 | 8080 | Auth, document upload, REST API, orchestration |
| `frontend/` | React + Vite | 5173 | Web UI (chat, document list, auth) |

The backend calls the AI service over HTTP; the frontend calls the backend.
LLM/embeddings run through [OpenRouter](https://openrouter.ai) (OpenAI-compatible).

## File structure

```
.
├── docker-compose.yml          # Run all three services + optional Postgres
├── .env.example                # Root env template (copy to .env for compose)
│
├── ai-service/                 # Python FastAPI RAG service
│   ├── app/
│   │   ├── main.py             # FastAPI app, endpoints, client wiring
│   │   ├── config.py           # Settings (env vars, provider/model selection)
│   │   ├── models.py           # Pydantic request/response schemas
│   │   ├── indexing.py         # Chunk embedding → ChromaDB
│   │   ├── retrieval.py        # Semantic search (vector + lexical fallback)
│   │   └── generation.py       # LLM answer generation with sources
│   ├── tests/                  # Pytest suite (mocked LLM, no key needed)
│   ├── .env                    # Your OpenRouter key (gitignored)
│   └── requirements.txt
│
├── backend/                    # Java Spring Boot REST API
│   └── src/main/java/com/example/aidocumentassistant/
│       ├── ai/                 # AiClient → HTTP client to ai-service
│       ├── auth/               # JWT register/login/refresh + /me
│       ├── chat/               # Chat, conversations, RAG tool + fallback
│       ├── document/           # Upload, listing, rename, download, reindex
│       └── user/               # User entity + repository
│
└── frontend/                   # React SPA
    └── src/
        ├── api.js              # API client (401 refresh interceptor)
        ├── App.jsx             # Routing
        ├── pages/              # Login, Register, Dashboard, Conversation history
        └── components/         # TopBar, ChatPanel, DocumentPanel, AuthForm
```

## Quick start (Docker — recommended)

```bash
cp .env.example .env
# edit .env → set OPENROUTER_API_KEY (get a free key at https://openrouter.ai/keys)

docker compose up --build
```

Then open **http://localhost:5173**. Postgres is used in the containerized setup
(instead of the local H2 default).

## Quick start (local, no Docker)

Run each service in its own terminal, in this order.

**1. AI service (port 8000)**

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # macOS/Linux
pip install -r requirements.txt

copy .env.example .env          # then set OPENROUTER_API_KEY in .env
# cp .env.example .env          # macOS/Linux

uvicorn app.main:app --reload --port 8000
```

**2. Backend (port 8080)**

```bash
cd backend
mvn spring-boot:run
```

Uses an embedded H2 file database by default (no Postgres needed) and stores
uploads under `backend/data/uploads/`.

**3. Frontend (port 5173)**

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

## Using the app

1. Register an account, then log in.
2. Upload a document on the documents panel. It is chunked, embedded, and
   indexed automatically (watch the status badge turn to "indexed").
3. Ask questions in chat. Answers cite sources as `[1] (filename, page N)`.
4. Rename / download / reindex / delete documents from the documents panel.

If the AI service is down, uploads still succeed (indexed later via the
reindex button) and chat falls back to an extractive, no-LLM answer.

## Configuration

| Variable | Where | Default | Purpose |
|---|---|---|---|
| `OPENROUTER_API_KEY` | `ai-service/.env` | — | OpenRouter key for chat + embeddings |
| `LLM_PROVIDER` | `ai-service/.env` | `openrouter` | `openrouter` or `openai` |
| `CHAT_MODEL` | `ai-service/.env` | `dots-studio/dots-3-note-preview:free` | Chat model (free tier) |
| `EMBEDDING_MODEL` | `ai-service/.env` | `openai/text-embedding-3-small` | Embedding model |
| `TOP_K` | `ai-service/.env` | `5` | Chunks retrieved per query |
| `APP_JWT_SECRET` | env / compose | dev secret | JWT signing key |
| `AI_SERVICE_BASE_URL` | env | `http://localhost:8000` | Backend → AI service URL |

The default chat model is free on OpenRouter (zero credits). Embeddings are not
available for free — `openai/text-embedding-3-small` costs ~$0.02 per 1M tokens
(typically fractions of a cent per document).

## Tests

```bash
cd ai-service
pytest tests -q                 # 13 tests, mocked LLM — no API key needed

cd backend
mvn test                        # 5 tests
```

## CI

Every push and pull request runs `.github/workflows/ci.yml`:

- **Backend** — `mvn test` on Java 17 (Temurin)
- **AI service** — `pytest` on Python 3.12
- **Frontend** — `eslint` + `vite build` on Node 20

## Retrieval pipeline

The AI service answers questions with a three-stage pipeline: hybrid
retrieval (Chroma vector search + BM25 lexical search, fused with Reciprocal
Rank Fusion) → optional LLM reranking of candidates → grounded generation
with `[n] (file, page)` citations. See `ai-service/README.md` for details
and tuning env vars.

## Spec deviations

See `ai-service/README.md`. In short: no LangChain (chunking lives in the
backend, so the service uses the OpenAI SDK + chromadb directly) and ChromaDB
runs embedded/in-process rather than as a standalone server.
