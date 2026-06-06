# LLM Conversation Memory & Search — Project Overview

## What We Are Building

A personal tool that lets you **index, summarize, and semantically search your LLM conversations**.

You paste raw conversation text, the app summarizes it, chunks it, stores embeddings in pgvector, and lets you search across all indexed conversations using natural language — not just keywords.

This is a **RAG (Retrieval-Augmented Generation)** pipeline, which is the most in-demand agentic pattern in the industry right now.

---

## Technology Choices

Each technology was chosen for a specific reason:

| Technology | Why |
|---|---|
| **LangChain4j** | Orchestrates the multi-step ingestion pipeline (fetch → chunk → summarize → embed → store). Abstracts the model layer so the underlying LLM is swappable with no changes to business logic. |
| **pgvector** | PostgreSQL extension that adds a `vector` column type and KNN similarity search. Chosen over Redis Stack because `langchain4j-redis` was dropped in LangChain4j 1.x. Postgres is already running — no new infrastructure needed. |
| **OpenAI / Ollama** | Summarization model + embedding model. Interchangeable via LangChain4j's model abstraction. |
| **Spring Boot** | REST API layer. |
| **Docker** | Packages the app and Redis as a single runnable unit. |
| **Kubernetes** | Allows ingestion workers and the search API to scale independently. |

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                  INPUT SOURCES                       │
│   [Claude Share URL]     [Exported JSON File]        │
└──────────────┬──────────────────────┬───────────────┘
               │                      │
               ▼                      ▼
┌─────────────────────────────────────────────────────┐
│              INGESTION PIPELINE (LangChain4j)        │
│                                                      │
│  1. Fetch / Parse conversation                       │
│  2. Summarize conversation  ──► LLM (OpenAI/Ollama) │
│  3. Chunk into segments                              │
│  4. Generate embeddings   ──► Embedding Model        │
│  5. Store in pgvector (chunk_embeddings table)       │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│              SEARCH API (Spring Boot)                │
│                                                      │
│  User query ──► Embed query ──► Similarity Search    │
│              ──► Return ranked conversation chunks   │
└─────────────────────────────────────────────────────┘
```

---

## Phases

The project is built **incrementally**. Each phase produces something that works and can be demonstrated. You never break what you already have.

### Phase 1 — MVP: Paste, Store, Keyword Search ✅ Complete
> **Goal:** Get something working end to end. No AI yet. No fancy search. Just the skeleton.

- Spring Boot REST API with POST / GET list / GET search / DELETE endpoints
- PostgreSQL with `tsvector` generated column + GIN index for keyword search
- Direct OpenAI HTTP call for summarization (no LangChain), with `[SUMMARIZATION_FAILED]` fallback so failed rows are still stored
- Centralized `@RestControllerAdvice` for validation (400) and not-found (404) error responses
- Smoke tests in `http/api-tests.http` (VS Code REST Client)

👉 See [PHASE_1.md](./PHASE_1.md)

---

### Phase 2 — LangChain4j Pipeline + Semantic Search ✅ Complete
> **Goal:** Wire a real pipeline into the existing POST endpoint. Introduce LangChain4j and pgvector.

- LangChain4j orchestrates: summarize → chunk → embed
- pgvector (Postgres extension) stores chunk embeddings with conversationId metadata
- `GET /api/v1/conversations/search` — semantic search, returns summaries ranked by similarity
- `GET /api/v1/conversations/search/keyword` — keyword search preserved at new path
- Two-step retrieval: search returns summaries, full conversation fetched on demand by ID
- No new input source — raw text paste from Phase 1 feeds the pipeline

👉 See [PHASE_2.md](./PHASE_2.md)

---

### Phase 3 — Docker + Kubernetes
> **Goal:** Make it production-deployable and demonstrate DevOps maturity.

- Dockerfile for the Spring Boot app
- Docker Compose for local dev (app + Postgres/pgvector)
- Kubernetes manifests for deployment
- Separate scaling: ingestion workers vs search API
- Health checks, config maps, secrets management

👉 See [PHASE_3.md](./PHASE_3.md)

---

### Phase 4 — Experimental: Input Sources + Redis Stack
> **Goal:** Add structured ingestion and explore a purpose-built vector store. Post-MVP.

- Exported Claude JSON file upload
- Claude share URL fetch (Jsoup HTML scraping)
- Redis Stack as an alternative vector store (experimental swap from pgvector)

👉 See [PHASE_4.md](./PHASE_4.md)

---

## Tech Stack Summary

```
Language:        Java 21
Framework:       Spring Boot 3.x
AI Orchestration: LangChain4j
Database (MVP):  PostgreSQL
Vector Search (Phase 2+): pgvector (Postgres extension)
Models:          OpenAI gpt-4o-mini (summarization)
                 OpenAI text-embedding-3-small (embeddings)
                 → Swappable to Ollama via LangChain4j model abstraction
Build Tool:      Gradle (Kotlin DSL)
Containerization: Docker, Docker Compose
Orchestration:   Kubernetes (Phase 3)
```

---

## Repository Structure (Target)

```
llm-memory-search/
├── README.md                    ← You are here
├── PHASE_1.md
├── PHASE_2.md
├── PHASE_3.md
├── PHASE_4.md
├── src/
│   └── main/java/
│       └── com/llmmemory/
│           ├── conversation/    ← Entities, repos, service, controller
│           ├── processing/      ← ChunkingService, SummarizationService, EmbeddingService
│           │   ├── config/      ← LangChain4jConfig (ChatModel, EmbeddingModel, PgVectorEmbeddingStore)
│           │   ├── exception/   ← SummarizationException
│           │   └── service/
│           ├── search/          ← SearchService (semantic KNN search)
│           ├── shared/          ← GlobalExceptionHandler, domain exceptions
│           └── ingestion/       ← File/URL parse (Phase 4)
├── docker-compose.yml           ← Phase 3
├── Dockerfile                   ← Phase 3
└── k8s/                         ← Phase 3
```
