# Phase 2 — LangChain4j Pipeline + Semantic Search

> **Status: 🔲 Not started.**

## Goal

Phase 2 wires a LangChain4j pipeline and pgvector store into the existing POST endpoint from Phase 1. No new input source is added — the same raw text paste already works. The value is what happens after the text arrives: proper chunking, per-chunk summarization, embedding, and semantic search.

Phase 1 used a direct `RestTemplate` call and Postgres `tsvector` search on purpose — the verbosity and keyword gaps make the Phase 2 abstractions concrete rather than theoretical.

---

## What Changes

The existing `POST /api/v1/conversations` endpoint gets extended: after storing the conversation (Phase 1 behaviour), the processing pipeline runs — chunks the raw content, summarizes the conversation, embeds the chunks, and stores the result in pgvector.

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

## Vector Store (pgvector)

The original spec called for Redis Stack (RediSearch + RedisJSON) as the vector store. In LangChain4j 1.x, Redis moved to a community module (`langchain4j-redis`) versioned independently from the BOM — it is not listed in the BOM and must be added with an explicit version. pgvector was chosen instead because Postgres is already running and `langchain4j-pgvector` is fully supported by the BOM, requiring no new infrastructure or explicit version pinning.

pgvector is a PostgreSQL extension that adds a `vector` column type and KNN similarity search. LangChain4j has first-class support via `langchain4j-pgvector`. Since Postgres is already running, no new infrastructure is needed.

Each chunk is stored as a row in a `vector_store` table managed by LangChain4j:

| Column | Type | Description |
|---|---|---|
| `embedding_id` | uuid | Primary key |
| `embedding` | vector(1536) | The float vector — 1536 dims for `text-embedding-3-small` |
| `content` | text | The raw chunk text |
| `metadata` | json | conversation_id, title, chunk_index, summary |

The pgvector extension must be enabled in Postgres before the app starts:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

---

## Semantic Search

`GET /api/v1/conversations/search?q=...` in Phase 2 embeds the query and runs a KNN similarity search in pgvector, replacing the Phase 1 Postgres `tsvector` approach. A query like "neural network optimization" surfaces a chunk about "machine learning model training" — semantic match, not token match.

Search uses a **two-step retrieval flow**:

1. **Search** — `GET /api/v1/conversations/search?q=...` embeds the query, runs KNN against the chunk embeddings, walks up to the parent conversation via `conversation_id` in metadata, deduplicates, and returns a list of `ConversationResponse` objects (id, title, summary, createdAt). Raw content is never returned here.
2. **Full fetch** — if the user wants the full conversation, they call `GET /api/v1/conversations/{id}`. That endpoint already exists from Phase 1.

`ConversationResponse` already excludes `rawContent`, so no new DTO is needed for search results.

---

## API Endpoints

### POST /api/v1/conversations
Unchanged from Phase 1 — raw text paste. Now also triggers the LangChain4j processing after storing.

### GET /api/v1/conversations/search?q=your+query
Semantic search via pgvector KNN. Returns `List<ConversationResponse>` — id, title, summary, createdAt. No raw content.

### GET /api/v1/conversations/{id}
Unchanged from Phase 1. Returns the full conversation including `rawContent`. Called when the user selects a result from the search list.

### GET /api/v1/conversations
Unchanged from Phase 1.

---

## Package Structure

```
src/main/java/com/llmmemory/
├── processing/
│   ├── config/
│   │   └── LangChain4jConfig.java     ← ChatModel, EmbeddingModel, PgVectorEmbeddingStore beans
│   ├── exception/
│   │   └── SummarizationException.java
│   └── service/
│       ├── ChunkingService.java       ← LangChain4j DocumentSplitter wrapper
│       ├── SummarizationService.java  ← LangChain4j ChatModel wrapper
│       └── EmbeddingService.java      ← LangChain4j EmbeddingModel wrapper
│
└── search/
    └── service/
        └── SearchService.java         ← Embed query, KNN search, return ranked conversations
```

---

## Dependencies (build.gradle.kts)

```kotlin
// LangChain4j — BOM pins all module versions in sync
// Note: langchain4j-redis is a community module outside the BOM — pgvector used instead
implementation(platform("dev.langchain4j:langchain4j-bom:1.14.1"))
implementation("dev.langchain4j:langchain4j")
implementation("dev.langchain4j:langchain4j-open-ai")
implementation("dev.langchain4j:langchain4j-pgvector")
```

---

## Phase 2 Done When...

- [ ] POST raw text → conversation chunked, summarized, embedded, stored in pgvector
- [ ] Semantic search returns results that keyword search would miss
- [ ] Search returns summaries only; full conversation fetched separately by ID
- [ ] Swapping OpenAI for Ollama requires only a config change

---

## What Phase 2 Does NOT Do

- No new input sources — file upload and URL ingestion are Phase 5
- No Redis — `langchain4j-redis` is a community module outside the BOM; pgvector is used instead
- No agent decision-making (Phase 3)
- No Docker Compose (Phase 4)
- No Kubernetes (Phase 4)
