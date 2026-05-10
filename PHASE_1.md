# Phase 1 — MVP: Paste, Store, Keyword Search

> **Status: ✅ Complete.** All endpoints (POST, GET list, GET search, DELETE) implemented and verified via `http/api-tests.http`.

## Goal

Phase 1 establishes the end-to-end skeleton — Spring Boot + PostgreSQL + a direct summarization call — before LangChain4j, Redis, embeddings, or agents enter the stack. Every later phase builds on this foundation.

---

## What Phase 1 Is

- Spring Boot REST API: `POST`, `GET` list, `GET` search, `DELETE` (details under [API Endpoints](#api-endpoints))
- PostgreSQL with two tables: `conversations` and `conversation_chunks`
- Summarization via a direct OpenAI HTTP call — no framework, just `RestTemplate`
- Keyword search via a Postgres `tsvector` generated column with a GIN index

---

## Why PostgreSQL First (Not Redis)

Starting with PostgreSQL makes the architecture progression deliberate. PostgreSQL's `tsvector` full-text search is keyword-based — it matches exact words. This creates a clear, demonstrable contrast when Redis Vector Store is introduced in Phase 2 with semantic search, which understands meaning rather than just matching tokens.

---

## Data Model

### Table: `conversations`

```sql
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255),
    source      VARCHAR(50),        -- 'paste', 'url', 'file' (url/file come in Phase 2)
    raw_content TEXT NOT NULL,
    summary     TEXT,
    created_at  TIMESTAMP DEFAULT now(),
    search_vector TSVECTOR           -- PostgreSQL full-text search index
);

CREATE INDEX idx_search_vector ON conversations USING GIN(search_vector);
```

### Table: `conversation_chunks`

```sql
CREATE TABLE conversation_chunks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID REFERENCES conversations(id),
    chunk_index         INT,
    content             TEXT NOT NULL,
    created_at          TIMESTAMP DEFAULT now()
);
```

Chunks exist now (rather than in Phase 2) so the schema is in place when semantic search lands. Phase 1 stores them as raw fixed-size slices; Phase 2 adds embeddings on the same table.

---

## API Endpoints

### POST /api/v1/conversations
Accepts a conversation in raw text form. Stores it, generates a summary, indexes it.

**Request body:**
```json
{
  "title": "My conversation about Spring Boot",
  "source": "paste",
  "rawContent": "User: How do I set up Spring Boot?\nAssistant: First, go to start.spring.io..."
}
```

**Response:**
```json
{
  "id": "uuid-here",
  "title": "My conversation about Spring Boot",
  "summary": "A conversation about setting up a Spring Boot project...",
  "createdAt": "2025-04-27T10:00:00Z"
}
```

### GET /api/v1/conversations
Returns a paginated list of all stored conversations with their summaries.

**Query params:** `page`, `size`

### GET /api/v1/conversations/search
Keyword search across `title` and `raw_content`. Backed by a Postgres `tsvector`
generated column with a GIN index.

**Query params:** `query` (the search term, required), `page`, `size`

**Response:** the same `PagedResponse<ConversationResponse>` shape as the list endpoint.

### DELETE /api/v1/conversations/{id}
Removes a conversation and all its chunks in a single transaction.

- **204 No Content** on success
- **404 Not Found** with `{ "error": "Conversation not found: <id>" }` if the id does not exist

The service deletes chunks first because the FK on `conversation_chunks.conversation_id`
has no `ON DELETE CASCADE`. Adding cascade is deferred — for Phase 1 the explicit
two-step delete is acceptable and easier to reason about.

---

## Summarization in Phase 1

Direct HTTP call to `https://api.openai.com/v1/chat/completions` via `RestTemplate`. No LangChain in Phase 1 — the abstraction lands in Phase 2 once the verbose baseline exists for comparison.

Summarization prompt (Phase 1):
```
Summarize the following conversation in 2-3 sentences.
Focus on the main topic discussed and any conclusions reached.

Conversation:
{raw_content}
```

### Failure handling — SummarizationException

`SummarizationService.summarize()` throws a custom checked `SummarizationException` when:
- the OpenAI API returns a 4xx/5xx
- a network error occurs (timeout, connection refused, etc.)
- the response body is missing the expected `choices[0].message.content` shape

`ConversationService` catches this and stores the conversation with `summary = "[SUMMARIZATION_FAILED]"` so the row is still saved and queryable.

**Retry plan (Phase 2+):** an overnight cron job filters `conversations` where `summary = '[SUMMARIZATION_FAILED]'` and re-requests summarization for each. A full dead-letter queue is deferred until volume justifies it.

### Validation error responses — GlobalExceptionHandler

`GlobalExceptionHandler` (in `shared/exception`) is a `@RestControllerAdvice` that catches `MethodArgumentNotValidException` from `@Valid` failures on request bodies. It returns 400 with a `Map<String, String>` of `field → message`, e.g. `{ "title": "must not be blank" }`. It also handles `ConversationNotFoundException` → 404 and `DuplicateTitleException` → 409, both with `{ "error": "..." }`. Catch-all and additional per-domain handlers get added here as needed.

### Title uniqueness — defense in depth

Conversations must have unique titles. Enforced at two layers:

1. **DB constraint:** `UNIQUE` on `conversations.title` is the source of truth. Concurrent inserts can't both succeed.
2. **App-level check:** `ConversationService.createConversation` calls `existsByTitle` before the INSERT and throws `DuplicateTitleException` → 409 if the title is taken. Cleaner error response than letting the DB throw and translating later.

`ddl-auto=update` is unreliable for adding unique constraints to existing tables, so the constraint was applied manually:
```sql
ALTER TABLE conversations ADD CONSTRAINT uk_conversations_title UNIQUE (title);
```
Phase 2+ replaces manual DDL with Flyway migrations.

### Search storage — generated tsvector column

`conversations.search_vector` is a Postgres `GENERATED ALWAYS AS (...) STORED` column derived from `title || ' ' || raw_content`. JPA does not map it (no field on the `Conversation` entity). Postgres recomputes it on every insert/update; the GIN index `idx_conversations_search_vector` makes `@@ plainto_tsquery(...)` matches fast. The migration was applied manually via psql in Phase 1; a real migration tool (Flyway/Liquibase) lands in Phase 2+.

### Configuration

`OpenAiConfig`:
- Binds `openai.api.key`, `openai.api.url`, `openai.api.model` from `application.properties`
- Exposes `RestTemplate` as a Spring bean for injection into `SummarizationService`

---

## Chunking in Phase 1

Fixed-size split: every N characters where N is `app.chunking.size` (default 500). Each chunk becomes a row in `conversation_chunks` with its `chunk_index`. Semantic chunking and embeddings land in Phase 2.

---

## What Phase 1 Does NOT Do

- No URL fetching (Phase 2)
- No file upload (Phase 2)
- No embeddings (Phase 2)
- No semantic search (Phase 2)
- No agents (Phase 3)
- No Docker (Phase 4)
