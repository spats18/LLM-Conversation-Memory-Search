# Phase 1 — MVP: Paste, Store, Keyword Search

> **Status: ✅ Complete.**

## Goal

Establish the end-to-end skeleton — Spring Boot + PostgreSQL + a direct OpenAI summarization call — before LangChain4j, embeddings, or semantic search enter the stack.

---

## What Was Built

- Spring Boot REST API: POST, GET list, GET search, DELETE
- PostgreSQL with two tables: `conversations` and `conversation_chunks`
- Summarization via a direct OpenAI HTTP call (`RestTemplate`) — no framework
- Keyword search via a Postgres `tsvector` generated column with a GIN index
- `GlobalExceptionHandler` (`@RestControllerAdvice`) for consistent 400/404/409 error responses

---

## Data Model

### `conversations`

```sql
CREATE TABLE conversations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(255) UNIQUE NOT NULL,
    raw_content   TEXT NOT NULL,
    summary       TEXT,
    created_at    TIMESTAMP DEFAULT now(),
    search_vector TSVECTOR GENERATED ALWAYS AS (
                    to_tsvector('english', coalesce(title,'') || ' ' || coalesce(raw_content,''))
                  ) STORED
);

CREATE INDEX idx_conversations_search_vector ON conversations USING GIN(search_vector);
```

### `conversation_chunks`

```sql
CREATE TABLE conversation_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES conversations(id),
    chunk_index     INT,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT now()
);
```

Chunks are created alongside conversations so the schema is ready for Phase 2 embeddings without a migration.

---

## API Endpoints

### POST /api/v1/conversations

```json
// Request
{ "title": "Spring Boot setup", "rawContent": "User: How do I..." }

// Response
{ "id": "uuid", "title": "Spring Boot setup", "summary": "...", "createdAt": "..." }
```

### GET /api/v1/conversations
Paginated list of all conversations. Query params: `page`, `size`.

### GET /api/v1/conversations/search/keyword?q=query
Keyword search via `tsvector` + GIN index. Returns `PagedResponse<ConversationResponse>`.

### DELETE /api/v1/conversations/{id}
Removes conversation and all its chunks in a single transaction. Returns 204 or 404.

---

## Summarization

Direct HTTP POST to OpenAI's chat completions endpoint via `RestTemplate`. On failure, stores `[SUMMARIZATION_FAILED]` so the conversation is still saved and queryable.

Replaced by LangChain4j `ChatModel` in Phase 2.
