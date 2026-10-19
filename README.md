# AI Document Assistant

A full-stack RAG (retrieval-augmented generation) app. Upload documents (PDF,
TXT, DOCX), and ask questions in chat — the system retrieves the most relevant
passages from your documents and answers with grounded citations (file + page).

Three services:

| Service | Tech | Port | Role |
|---|---|---|---|
| `ai-service/` | Python + FastAPI + Postgres (pgvector) | 8000 | Embeddings, hybrid retrieval, reranking, LLM answer generation |
| `backend/` | Java + Spring Boot + H2/Postgres | 8080 | Auth, document upload, REST API, orchestration, agent tools |
| `frontend/` | React + Vite | 5173 | Web UI (chat, document list, auth) |

The backend calls the AI service over HTTP; the frontend calls the backend.
LLM/embeddings run through [OpenRouter](https://openrouter.ai) (OpenAI-compatible).

## File structure

```
.
├── docker-compose.yml          # Run all services + Postgres (pgvector image)
├── .env.example                # Root env template (copy to .env for compose)
│
├── ai-service/                 # Python FastAPI RAG service
│   ├── app/
│   │   ├── main.py             # FastAPI app, endpoints, client wiring
│   │   ├── config.py           # Settings (env vars, provider/model selection)
│   │   ├── models.py           # Pydantic request/response schemas
│   │   ├── store.py            # VectorStore protocol + Postgres/pgvector impl
│   │   ├── indexing.py         # Chunk embedding → vector store
│   │   ├── retrieval.py        # Hybrid search: vector + BM25, RRF fusion
│   │   ├── corpus.py           # In-memory BM25 index (kept in sync with the store)
│   │   ├── rerank.py           # Optional LLM reranking of candidates
│   │   └── generation.py       # LLM answers: documents + web context variants
│   ├── tests/                  # Pytest suite (mocked LLM, no key needed)
│   ├── .env                    # Your OpenRouter key (gitignored)
│   └── requirements.txt
│
├── backend/                    # Java Spring Boot REST API
│   └── src/main/java/com/example/aidocumentassistant/
│       ├── ai/                 # AiClient → HTTP client to ai-service
│       │   ├── agent/          # Tools: RAG, Web Search (opt-in), SQL (stub)
│       │   └── websearch/      # Web search JSON client (SearXNG-compatible)
│       ├── auth/               # JWT register/login/refresh + /me
│       ├── chat/               # Chat, conversations, RAG tool + fallback
│       ├── document/           # Upload, extraction (PDFBox + Tesseract OCR),
│       │                       #   chunking, rename, download, reindex
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

docker compose up --build -d      # postgres (pgvector), ai-service, backend

cd frontend
npm install
npm run dev                       # Vite dev server on http://localhost:5173
```

Then open **http://localhost:5173**. Postgres is used in the containerized setup
(instead of the local H2 default). The frontend runs via Vite dev server
(hot reload); no frontend container.

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
   **Scanned PDFs** (image-only pages) are OCR'd automatically with
   Tesseract (`vie+eng` in Docker) before indexing.
3. Ask questions in chat. The **RAG** tool answers from your documents with
   `[1] (filename, page N)` citations. The **Web** tool (opt-in, see
   [Web search](#web-search)) searches the internet and answers with inline
   source links. Pick a tool explicitly with the chat toolbar, or let the
   agent route simple questions.
4. Rename / download / reindex / delete documents from the documents panel.

If the AI service is down, uploads still succeed (indexed later via the
reindex button) and chat falls back to an extractive, no-LLM answer. If
generation fails during a web search, the tool returns the top snippets with
links instead of an LLM answer.

## Configuration

| Variable | Where | Default | Purpose |
|---|---|---|---|
| `OPENROUTER_API_KEY` | root `.env` / `ai-service/.env` | — | OpenRouter key for chat + embeddings |
| `LLM_PROVIDER` | `ai-service/.env` | `openrouter` | `openrouter` or `openai` |
| `CHAT_MODEL` | `ai-service/.env` | `dots-studio/dots-3-note-preview:free` | Chat model (free tier) |
| `CHAT_REASONING_ENABLED` | `ai-service/.env` | `false` | Ask OpenRouter to skip hidden reasoning tokens |
| `EMBEDDING_MODEL` | `ai-service/.env` | `nvidia/nemotron-3-embed-1b:free` | Embedding model (free tier) |
| `TOP_K` | `ai-service/.env` | `5` | Chunks retrieved per query |
| `RERANK_ENABLED` | `ai-service/.env` | `true` | LLM reranking of retrieval candidates |
| `APP_JWT_SECRET` | env / compose | dev secret | JWT signing key |
| `AI_SERVICE_BASE_URL` | env | `http://localhost:8000` | Backend → AI service URL |
| `DATABASE_URL` | env / compose | — | Postgres DSN for the AI service's pgvector store |
| `WEB_SEARCH_BASE_URL` | env | *(blank = disabled)* | Backend → SearXNG-compatible JSON API; blank disables the Web tool |
| `WEB_SEARCH_MAX_RESULTS` | env | `5` | Web results considered per question |
| `APP_OCR_ENABLED` | env | `true` | OCR fallback for scanned/sparse PDF pages |
| `APP_OCR_LANGUAGE` | env | `vie+eng` | Tesseract languages (`vie+eng` in Docker) |
| `APP_OCR_MAX_PAGES` | env | `50` | OCR budget per document (bounds upload time) |

Both default chat and embedding models are free on OpenRouter (zero credits).
Note: the root `.env` (used by `docker compose`) must contain
`OPENROUTER_API_KEY` — copy `.env.example` to `.env` first.

## Tests

```bash
cd ai-service
pytest tests -q                 # 31 tests, mocked LLM — no API key needed
TEST_DATABASE_URL=... pytest tests/test_pgvector_store.py -q
                                # optional: 7 pgvector integration tests
                                # (skipped unless TEST_DATABASE_URL is set)

cd backend
mvn test                        # 91 tests (2 live smoke tests auto-skip
                                # when ai-service isn't running on :8000)
```

## CI

Every push and pull request runs `.github/workflows/ci.yml`:

- **Backend** — `mvn test` on Java 17 (Temurin)
- **AI service** — `pytest` on Python 3.12
- **Frontend** — `eslint` + `vite build` on Node 20

## Deploying (Vercel + Render + Neon)

Production shape: **Vercel** (frontend) → **Render** (`backend` + `ai-service`,
both Docker) → **Neon** (one Postgres database; Hibernate uses the default
schema, the AI service owns schema `ai_service`) → **Cloudinary** (uploads).

1. **Neon** — create a project, then one database. Note the **direct** endpoint
   (not `-pooler`): the AI service pools connections in-app. The pgvector
   extension is available on Neon; the service runs `CREATE EXTENSION` itself.
2. **Render** — add a Blueprint pointing at this repo (reads `render.yaml`).
   It creates `aidoc-ai-service` and `aidoc-backend`; fill in the `sync: false`
   env vars (Neon DSN/JDBC URL + credentials, `OPENROUTER_API_KEY`,
   `APP_JWT_SECRET`, Cloudinary keys, `APP_GOOGLE_CLIENT_ID`,
   `APP_CORS_ALLOWED_ORIGINS` = your Vercel origin). `AI_SERVICE_BASE_URL` is
   wired automatically between the two services.
3. **Vercel** — import `frontend/` (framework preset: Vite, builds `dist/`;
   `vercel.json` provides the SPA rewrite). Set `VITE_API_BASE_URL` to the
   Render backend URL (`https://aidoc-backend.onrender.com`).
4. **Google Cloud Console** — add the Vercel domain(s) to your OAuth Web
   client's authorized origins, and set the same client ID on both
   `APP_GOOGLE_CLIENT_ID` (Render) and `VITE_GOOGLE_CLIENT_ID` (Vercel).

CORS on the backend must list the exact Vercel origin
(`APP_CORS_ALLOWED_ORIGINS`, comma-separated). Free-tier Render instances
spin down when idle, so first requests after idle are slow (cold start).

## Retrieval pipeline

The AI service answers questions with a three-stage pipeline: hybrid
retrieval (pgvector cosine search + BM25 lexical search, fused with Reciprocal
Rank Fusion) → optional LLM reranking of candidates → grounded generation
with `[n] (file, page)` citations. See `ai-service/README.md` for details
and tuning env vars.

## Web search

The chat's **Web** tool is opt-in and disabled by default (no search engine
is bundled). Point `WEB_SEARCH_BASE_URL` at any
[SearXNG](https://github.com/searxng/searxng)-compatible JSON API to enable
it; the tool then queries it (keyless metasearch) and has the LLM draft a
grounded answer from the top snippets with inline `[n]` citations and URLs.
If the LLM call fails, the raw top snippets are returned instead. When
disabled, the agent automatically routes those questions to document search
(RAG) instead of dead-ending.

## OCR (scanned PDFs)

PDF pages that yield almost no embedded text are treated as scanned and
rasterized with PDFBox (300 DPI), then OCR'd with [Tesseract](https://github.com/tesseract-ocr/tesseract)
via Tess4J. Docker images ship with `vie+eng` language data out of the box.
Outside Docker, install Tesseract (e.g. `choco install tesseract` +
language packs) and point `APP_OCR_TESSDATA_DIR` at its tessdata folder;
without it, scanned uploads fail gracefully with a clear error while
text-based PDFs are unaffected. `APP_OCR_MAX_PAGES` bounds how many pages
of a single document get OCR'd (default 50) to keep upload times sane.

## Spec deviations

See `ai-service/README.md`. In short: no LangChain (chunking lives in the
backend, so the service uses the OpenAI SDK directly) and vectors live in
Postgres via pgvector (schema `ai_service`) rather than a dedicated vector
database.
