# LLM Conversation Memory & Search

A personal tool for indexing, summarizing, and semantically searching LLM conversations. Paste raw conversation text — the app summarizes it, chunks it, stores embeddings in pgvector, and lets you search across all indexed conversations using natural language, not keywords.

This is a RAG (Retrieval-Augmented Generation) pipeline built incrementally across four phases.

---

## Architecture

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

## Tech Stack

| Technology | Role |
|---|---|
| **Java 21 / Spring Boot 3.x** | REST API |
| **LangChain4j** | Orchestrates the ingestion pipeline; abstracts the model layer so OpenAI and Ollama are interchangeable |
| **PostgreSQL + pgvector** | Relational storage + KNN vector similarity search |
| **OpenAI / Ollama** | Summarization (chat model) + embeddings (embedding model) |
| **Docker + Docker Compose** | Containerized deployment |
| **Gradle (Kotlin DSL)** | Build tool |

---

## Phases

| Phase | What it adds | Status |
|---|---|---|
| 1 — **[MVP](./PHASE_1.md)** | Spring Boot API, PostgreSQL, keyword search, direct OpenAI summarization | Complete |
| 2 — **[Semantic Search](./PHASE_2.md)** | LangChain4j pipeline, pgvector embeddings, semantic search | Complete |
| 3 — **[Docker](./PHASE_3.md)** | Multi-stage Dockerfile, Docker Compose | Complete |
| 4 — **[Input Sources](./PHASE_4.md)** | JSON file upload, Claude share URL | Not started |

---

## Repository Structure

```
llm-memory-search/
├── src/main/java/com/llmmemory/
│   ├── conversation/    ← Entities, repos, service, controller
│   ├── processing/      ← ChunkingService, SummarizationService, EmbeddingService, LangChain4jConfig
│   ├── search/          ← SearchService (semantic KNN search)
│   ├── shared/          ← GlobalExceptionHandler, domain exceptions
│   └── ingestion/       ← File/URL parse (Phase 4)
├── Dockerfile
├── docker-compose.yml
└── init.sql
```
