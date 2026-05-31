# Phase 5 — Experimental: Input Sources + Redis Stack Vector Store

> **Status: 🔲 Post-MVP. Start after Phase 4 is complete.**

## Goal

Two independent workstreams, both optional extensions once the core project is done:

1. **Additional input sources** — file upload and URL fetch on top of the existing pipeline
2. **Redis Stack as the vector store** — replace pgvector with Redis Stack to learn how a purpose-built vector database works, and understand what pgvector gave up to stay simple

---

## Part A: Additional Input Sources

### Source 1: Exported Claude JSON

`POST /api/v1/conversations/ingest-file` accepts a multipart upload of an exported Claude JSON file. Jackson deserializes it into a `ParsedConversation`, which feeds the existing pipeline.

Expected structure:

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

**What's needed:**
- `ParsedConversation` + `ConversationTurn` model classes in `ingestion/`
- `FileParserService` — Jackson deserialization into `ParsedConversation`
- `IngestionService` — flattens turns into text, hands off to the pipeline

### Source 2: Claude Share URL

`POST /api/v1/conversations/ingest-url` accepts a Claude share URL (e.g. `https://claude.ai/share/some-id`). `UrlFetcherService` fetches the page via Jsoup, parses the HTML to extract title and turns, and feeds the same pipeline.

The HTML parser is intentionally fragile — Claude's share page structure is undocumented and can change at any time. This is documented in the code as a known tradeoff.

**What's needed:**
- `UrlFetcherService` in `ingestion/` — Jsoup HTTP GET + HTML parse
- Dependency already present: `implementation("org.jsoup:jsoup:1.17.2")`

---

## Part B: Redis Stack as Vector Store

### Why Do This

pgvector was chosen for Phase 2 because Postgres was already running and the LangChain4j BOM managed the dependency cleanly. The learning tradeoff: pgvector abstracts away most of the vector store complexity. Redis Stack exposes it.

Swapping to Redis Stack here teaches:
- How RediSearch vector indexes work (FLAT vs HNSW, dimensions, distance metrics)
- What a dual-write architecture looks like in practice — Postgres for relational data, Redis for vectors, both staying in sync
- The cost of a purpose-built vector store vs a Postgres extension
- Why companies choose Redis Stack over pgvector at scale (latency, throughput, operational separation)

### What Redis Stack Stores

Each chunk becomes a JSON document in Redis with a vector field:

```json
{
  "chunk_id": "uuid",
  "conversation_id": "uuid",
  "conversation_title": "My Spring Boot conversation",
  "chunk_content": "The raw chunk text",
  "summary": "A summary of this chunk",
  "embedding": [0.123, -0.456, ...]
}
```

A RediSearch vector index is created once at startup:

```
FT.CREATE idx:chunks ON JSON
  SCHEMA $.embedding AS embedding VECTOR FLAT 6
    TYPE FLOAT32 DIM 1536 DISTANCE_METRIC COSINE
```

KNN search queries run against this index — LangChain4j's `langchain4j-redis` community module handles index creation and query execution.

### Dual-Write Architecture

With Redis Stack, both stores must stay in sync:

| Operation | Postgres | Redis |
|---|---|---|
| Ingest | Store conversation + chunks | Store chunk embeddings |
| Delete | Remove conversation + chunks | Remove chunk documents |
| Search | Relational queries (list, filter) | Vector KNN (semantic search) |

Deletion is the tricky part — if the Redis delete fails after Postgres succeeds, embeddings are orphaned and surface in search results for conversations that no longer exist. The service layer handles both deletes in order and throws if Redis fails.

### Dependency

`langchain4j-redis` is a community module versioned independently from the BOM:

```kotlin
implementation("dev.langchain4j:langchain4j-redis:1.0.0-beta2")
```

### Running Redis Stack Locally

```bash
docker run -d --name redis-stack \
  -p 6379:6379 \
  -p 8001:8001 \
  redis/redis-stack:latest
```

Port 8001 is RedisInsight — inspect stored documents, vector indexes, and run queries visually.

---

## Phase 5 Done When...

**Part A:**
- [ ] POST an exported JSON file → parsed, chunked, embedded, stored
- [ ] POST a Claude share URL → fetched, parsed, chunked, embedded, stored

**Part B:**
- [ ] pgvector replaced by Redis Stack as the embedding store
- [ ] RediSearch index created at startup
- [ ] Semantic search returns correct results via Redis KNN
- [ ] Delete removes from both Postgres and Redis atomically
- [ ] RedisInsight shows stored chunk documents and the vector index
