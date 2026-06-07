# Phase 2 — LangChain4j Pipeline + Semantic Search

> **Status: ✅ Complete.**

## Goal

Wire a LangChain4j pipeline into the existing POST endpoint and replace keyword search with pgvector KNN semantic search.

---

## What Changes

`POST /api/v1/conversations` now triggers the full processing pipeline after storing the conversation. `GET /api/v1/conversations/search` switches to pgvector KNN semantic search. Keyword search is preserved at `GET /api/v1/conversations/search/keyword`.

---

## Ingestion Pipeline

```
rawContent
    │
    ▼
SummarizationService   ← ChatModel (LangChain4j) — summary stored on Conversation
    │
    ▼
ChunkingService        ← DocumentByCharacterSplitter, 500-char chunks, 50-char overlap
    │
    ▼
EmbeddingService       ← EmbeddingModel, stores vectors in pgvector with metadata
    │
    ▼
PgVectorEmbeddingStore ← chunk_embeddings table (managed by LangChain4j)
```

---

## Model Configuration

`ChatModel` and `EmbeddingModel` are separate Spring beans in `LangChain4jConfig`. Swapping OpenAI for Ollama requires only a property change — no business logic changes.

```java
// OpenAI (default)
OpenAiChatModel.builder().apiKey(...).modelName("gpt-4o-mini").build();
OpenAiEmbeddingModel.builder().apiKey(...).modelName("text-embedding-3-small").build();

// Ollama alternative
OllamaChatModel.builder().baseUrl("http://localhost:11434").modelName("llama3.1:8b").build();
OllamaEmbeddingModel.builder().baseUrl("http://localhost:11434").modelName("nomic-embed-text").build();
```

---

## Vector Store

pgvector is a PostgreSQL extension that adds a `vector` column type and KNN similarity search. Each chunk is stored in the `chunk_embeddings` table:

| Column | Type | Description |
|---|---|---|
| `embedding_id` | uuid | Primary key |
| `embedding` | vector(1536) | 1536-dim float vector (`text-embedding-3-small`) |
| `content` | text | Raw chunk text |
| `metadata` | json | `conversationId`, `chunkId`, `chunkIndex` |

---

## Semantic Search

`GET /api/v1/conversations/search?q=...` embeds the query and runs KNN similarity search in pgvector. A query like "neural network optimization" surfaces a chunk about "machine learning model training" — semantic match, not token match.

Two-step retrieval:
1. **Search** — returns `List<ConversationResponse>` (id, title, summary, createdAt). No raw content.
2. **Full fetch** — `GET /api/v1/conversations/{id}` returns the full conversation on demand.

Tuning parameters in `application.properties`:
- `app.search.max-results` (default: 20) — chunk matches fetched before deduplication
- `app.search.min-score` (default: 0.65) — cosine similarity threshold (0–1)

---

## API Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/v1/conversations` | Ingest raw text, runs full pipeline |
| `GET /api/v1/conversations/search?q=` | Semantic search via pgvector KNN |
| `GET /api/v1/conversations/search/keyword?q=` | Keyword search via tsvector |
| `GET /api/v1/conversations/{id}` | Full conversation including raw content |
| `GET /api/v1/conversations` | Paginated list |
