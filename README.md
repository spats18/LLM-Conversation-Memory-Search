# LLM Conversation Memory & Search — Project Overview

## What We Are Building

A personal tool that lets you **index, summarize, and semantically search your LLM conversations**.

You paste raw conversation text, the app chunks it, summarizes each chunk, stores embeddings in Redis, and lets you search across all indexed conversations using natural language — not just keywords.

This is a **RAG (Retrieval-Augmented Generation)** pipeline, which is the most in-demand agentic pattern in the industry right now.

---

## Technology Choices

Each technology was chosen for a specific reason:

| Technology | Why |
|---|---|
| **LangChain4j** | Orchestrates the multi-step ingestion pipeline (fetch → chunk → summarize → embed → store). Abstracts the model layer so the underlying LLM is swappable with no changes to business logic. |
| **Redis Stack** | Provides native vector similarity search via RediSearch. Also used as a cache to avoid re-processing conversations. |
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
│  2. Chunk into segments                              │
│  3. Summarize each chunk  ──► LLM (OpenAI/Ollama)   │
│  4. Generate embeddings   ──► Embedding Model        │
│  5. Store in Redis Vector Store                      │
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

### Phase 2 — LangChain4j Pipeline + Semantic Search
> **Goal:** Wire a real pipeline into the existing POST endpoint. Introduce LangChain4j and Redis.

- LangChain4j orchestrates: chunk → summarize → embed
- Redis Vector Store stores embeddings
- Semantic search replaces keyword search
- No new input source — raw text paste from Phase 1 feeds the pipeline

👉 See [PHASE_2.md](./PHASE_2.md)

---

### Phase 3 — Agentic Layer
> **Goal:** Add a real agent that decides when to use tools.

- Agent can decide: search your history before answering a new question
- Tools available to agent: `search_conversations`, `summarize_conversation`, `store_conversation`
- Agent workflow: user asks a question → agent checks memory → answers using past context
- This is where the project becomes genuinely "agentic"

👉 See [PHASE_3.md](./PHASE_3.md)

---

### Phase 4 — Docker + Kubernetes
> **Goal:** Make it production-deployable and demonstrate DevOps maturity.

- Dockerfile for the Spring Boot app
- Docker Compose for local dev (app + Redis)
- Kubernetes manifests for deployment
- Separate scaling: ingestion workers vs search API
- Health checks, config maps, secrets management

👉 See [PHASE_4.md](./PHASE_4.md)

---

### Phase 5 — Additional Input Sources
> **Goal:** Add structured ingestion on top of the existing pipeline. Post-MVP.

- Exported Claude JSON file upload
- Claude share URL fetch (Jsoup HTML scraping)

👉 See [PHASE_5.md](./PHASE_5.md)

---

## Build Approach

Each phase produces a working, demonstrable system. No phase breaks what the previous one built.

Rough timeline:
- Phase 1: ~1 week
- Phase 2: ~2 weeks
- Phase 3: ~1 week
- Phase 4: ~a few days
- Phase 5: ~1 week (post-MVP, time permitting)

---

## Tech Stack Summary

```
Language:        Java 21
Framework:       Spring Boot 3.x
AI Orchestration: LangChain4j
Database (MVP):  PostgreSQL
Database (Phase 2+): Redis Stack (with Vector Search)
Models:          OpenAI gpt-4o-mini (summarization)
                 OpenAI text-embedding-3-small (embeddings)
                 → Swappable to Ollama in Phase 2
Build Tool:      Gradle (Kotlin DSL)
Containerization: Docker, Docker Compose
Orchestration:   Kubernetes (Phase 4)
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
├── PHASE_5.md
├── src/
│   └── main/java/
│       └── com/llmmemory/
│           ├── conversation/    ← Entities, repos, service, controller (Phase 1)
│           ├── summarization/   ← Direct OpenAI call (Phase 1)
│           ├── pipeline/        ← LangChain4j chunk/embed/summarize (Phase 2)
│           ├── storage/         ← Redis vector store (Phase 2)
│           ├── search/          ← Semantic search (Phase 2)
│           ├── agent/           ← Agent + tools (Phase 3)
│           └── ingestion/       ← File/URL parse (Phase 5)
├── docker-compose.yml           ← Phase 4
├── Dockerfile                   ← Phase 4
└── k8s/                         ← Phase 4
```
