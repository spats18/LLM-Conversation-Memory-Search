# Phase 2 — Real Ingestion: URL Fetch + File Upload + LangChain4j + Redis

> **Status: 🔲 Not started.**

## Goal

Phase 2 replaces the manual pieces from Phase 1 with a proper ingestion pipeline:
- Two input sources: Claude share URL and exported JSON file
- LangChain4j for chunking, summarization, and embedding
- Redis Stack as the vector store
- Semantic search replacing keyword search

Phase 1 used a direct `RestTemplate` call and Postgres `tsvector` search on purpose — the verbosity and keyword gaps make the Phase 2 abstractions concrete rather than theoretical.

---

## Input Sources

Both sources produce the same internal `ParsedConversation`. The ingestion pipeline is source-agnostic.

```java
public class ParsedConversation {
    private String title;
    private String sourceUrl;       // null if from file
    private String sourceType;      // "url" or "file"
    private List<ConversationTurn> turns;
}

public class ConversationTurn {
    private String role;            // "user" or "assistant"
    private String content;
}
```

### Source 1: Claude Share URL

`POST /api/v1/conversations/ingest-url` accepts a Claude share URL (e.g. `https://claude.ai/share/some-id`). `UrlFetcherService` issues an HTTP GET via Jsoup, parses the HTML to extract the title and conversation turns, and hands a `ParsedConversation` to the ingestion pipeline.

The HTML parser is intentionally fragile — Claude's share page structure can change without notice. This is documented in the code as a known tradeoff, not a bug.

### Source 2: Exported Claude JSON

`POST /api/v1/conversations/ingest-file` accepts a multipart upload of an exported Claude JSON file. Jackson (already on the classpath) deserializes it. Expected structure:

```json
{
  "title": "Conversation about Spring Boot",
  "created_at": "2025-04-01T10:00:00Z",
  "messages": [
    { "role": "user", "content": "How do I set up Spring Boot?" },
    { "role": "assistant", "content": "First, go to start.spring.io..." }
  ]
}
```

---

## Ingestion Pipeline

```
ParsedConversation
       │
       ▼
  DocumentLoader         ← Convert ParsedConversation → LangChain4j Document
       │
       ▼
  TextSplitter           ← 500-token chunks, 50-token overlap
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
  "source_url": "https://claude.ai/share/...",
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

### POST /api/v1/conversations/ingest-url
```json
{ "url": "https://claude.ai/share/abc123" }
```

### POST /api/v1/conversations/ingest-file
Multipart form upload of the exported JSON file.

### GET /api/v1/conversations/search?q=your+query
Semantic search. Returns ranked results with relevance scores.

### GET /api/v1/conversations
Unchanged from Phase 1.

---

## Package Structure

```
src/main/java/com/llmmemory/
├── ingestion/
│   ├── IngestionService.java          ← Orchestrates the full pipeline
│   ├── UrlFetcherService.java         ← Jsoup fetch + HTML parse
│   ├── FileParserService.java         ← Jackson parse of exported JSON
│   ├── ParsedConversation.java        ← Common internal format
│   └── ConversationTurn.java
│
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
// HTML parsing
implementation("org.jsoup:jsoup:1.17.2")

// LangChain4j — BOM pins all module versions in sync
implementation(platform("dev.langchain4j:langchain4j-bom:1.14.1"))
implementation("dev.langchain4j:langchain4j")
implementation("dev.langchain4j:langchain4j-open-ai")
implementation("dev.langchain4j:langchain4j-redis")
```

---

## Phase 2 Done When...

- [ ] POST a Claude share URL → conversation fetched, parsed, chunked, summarized, embedded, stored in Redis
- [ ] POST an exported JSON file → same pipeline, different source
- [ ] Semantic search returns results that keyword search would miss
- [ ] Swapping OpenAI for Ollama requires only a config change
- [ ] RedisInsight shows stored embeddings correctly

---

## What Phase 2 Does NOT Do

- No agent decision-making (Phase 3)
- No Docker Compose (Phase 4)
- No Kubernetes (Phase 4)
