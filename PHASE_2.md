# Phase 2 — Real Ingestion: LangChain4j + Redis

> **Status: 🔲 Not started.**

## Goal

Phase 2 wires a LangChain4j pipeline and Redis vector store into the existing POST endpoint from Phase 1. No new input source is added — the same raw text paste already works. The value is what happens after the text arrives: proper chunking, per-chunk summarization, embedding, and semantic search.

Phase 1 used a direct `RestTemplate` call and Postgres `tsvector` search on purpose — the verbosity and keyword gaps make the Phase 2 abstractions concrete rather than theoretical.

---

## What Changes

The existing `POST /api/v1/conversations` endpoint gets extended: after storing the conversation (Phase 1 behaviour), the pipeline runs — chunks the raw content, summarizes each chunk, embeds it, and stores the result in Redis.

`GET /api/v1/conversations/search` switches from Postgres `tsvector` to a Redis KNN semantic search.

---

## Ingestion Pipeline

```
rawContent (from existing POST endpoint)
       │
       ▼
  ChunkingService        ← LangChain4j DocumentSplitter, 500-token chunks, 50-token overlap
       │
       ▼
  SummarizationService   ← Per-chunk LLM summarization (ChatLanguageModel)
       │
       ▼
  EmbeddingService       ← Per-chunk embedding (EmbeddingModel)
       │
       ▼
  RedisVectorStore       ← Store chunk, summary, embedding, metadata
```

Chunk overlap is 50 tokens so nothing is lost at split boundaries — each chunk shares a tail with its successor.

---

## Model Configuration

Both `ChatLanguageModel` and `EmbeddingModel` are Spring beans. The underlying provider is a configuration detail — swapping OpenAI for Ollama requires only a property change and a module swap.

```java
// OpenAI (default)
ChatLanguageModel chatModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o-mini")
    .build();

EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-3-small")
    .build();

// Ollama alternative (requires: implementation("dev.langchain4j:langchain4j-ollama"))
ChatLanguageModel chatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1:8b")
    .build();

EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("nomic-embed-text")
    .build();
```

---

## Redis Vector Store

Redis Stack (RediSearch + RedisJSON) provides native vector similarity search. LangChain4j has a first-class integration — no manual index management needed.

Run locally:
```bash
docker run -d --name redis-stack \
  -p 6379:6379 \
  -p 8001:8001 \
  redis/redis-stack:latest
```

Port 8001 is RedisInsight — a web UI for inspecting stored documents and embeddings.

Each chunk is stored as a Redis document:

```json
{
  "chunk_id": "uuid",
  "conversation_id": "uuid",
  "conversation_title": "My Spring Boot conversation",
  "chunk_content": "The raw chunk text",
  "summary": "A summary of this chunk",
  "embedding": [0.123, -0.456, ...],
  "created_at": "2025-04-27T10:00:00Z"
}
```

`embedding` is 1536 dimensions for `text-embedding-3-small`.

---

## Semantic Search

`GET /api/v1/conversations/search?q=...` in Phase 2 embeds the query and runs a KNN similarity search in Redis, replacing the Phase 1 Postgres `tsvector` approach. A query like "neural network optimization" surfaces a chunk about "machine learning model training" — semantic match, not token match.

---

## API Endpoints

### POST /api/v1/conversations
Unchanged from Phase 1 — raw text paste. Now also triggers the LangChain4j pipeline after storing.

### GET /api/v1/conversations/search?q=your+query
Semantic search via Redis KNN. Returns ranked results with relevance scores.

### GET /api/v1/conversations
Unchanged from Phase 1.

---

## Package Structure

```
src/main/java/com/llmmemory/
├── pipeline/
│   ├── ChunkingService.java           ← LangChain4j DocumentSplitter wrapper
│   ├── SummarizationService.java      ← LangChain4j ChatLanguageModel wrapper
│   └── EmbeddingService.java          ← LangChain4j EmbeddingModel wrapper
│
├── storage/
│   └── RedisVectorStoreService.java   ← Store and retrieve from Redis
│
└── search/
    └── SearchService.java             ← Embed query, KNN search, return ranked chunks
```

---

## Dependencies (build.gradle.kts)

```kotlin
// LangChain4j — BOM pins all module versions in sync
implementation(platform("dev.langchain4j:langchain4j-bom:1.14.1"))
implementation("dev.langchain4j:langchain4j")
implementation("dev.langchain4j:langchain4j-open-ai")
implementation("dev.langchain4j:langchain4j-redis")
```

---

## Phase 2 Done When...

- [ ] POST raw text → conversation chunked, summarized, embedded, stored in Redis
- [ ] Semantic search returns results that keyword search would miss
- [ ] Swapping OpenAI for Ollama requires only a config change
- [ ] RedisInsight shows stored embeddings correctly

---

## What Phase 2 Does NOT Do

- No new input sources — file upload and URL ingestion are Phase 5
- No agent decision-making (Phase 3)
- No Docker Compose (Phase 4)
- No Kubernetes (Phase 4)
