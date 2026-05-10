# Phase 1 — MVP: Paste, Store, Keyword Search

> **Status: ✅ Code complete.** All endpoints (POST, GET list, GET search, DELETE) are implemented and tested manually via `http/api-tests.http`. Two understanding-checkpoints remain in the "Done When..." list at the bottom — those are about being able to explain the system in an interview, not code state.

## Goal

Get something **working end to end** before introducing any complexity.

No LangChain yet. No Redis. No embeddings. No AI magic. Just a Spring Boot app that accepts a conversation, stores it, and lets you search it.

This phase is about understanding the **shape of the data** and building the skeleton that every later phase will build on.

---

## What You Will Build

- A Spring Boot REST API with three endpoints
- A PostgreSQL database with two tables
- A simple service that accepts raw conversation text, stores it, and lets you search by keyword
- A minimal summarization step using direct OpenAI API call (no framework — just an HTTP call)

By the end of Phase 1, you can:
1. POST a conversation → it gets stored with a summary
2. GET all conversations → paginated list
3. GET /search?query=your query → keyword match against summaries and content
4. DELETE a conversation by id → removes the conversation and its chunks

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

Why chunks? Because one conversation can be long. You will search at the chunk level in Phase 2 (with embeddings). In Phase 1, the chunk table just stores the raw pieces so you get used to thinking in chunks now.

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

Make a **direct HTTP call to the OpenAI API** from the Java service. No LangChain. No abstraction. Just `RestTemplate` or `WebClient` calling `https://api.openai.com/v1/chat/completions`.

This deliberate choice makes the value of LangChain4j's abstraction visible when it's introduced in Phase 2 — you will have already experienced the verbose alternative.

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

`ConversationService` catches this and stores the conversation with `summary = "[SUMMARIZATION_FAILED]"` so the row is still saved and queryable. A real dead-letter queue / retry mechanism is deferred to Phase 2.

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

Keep it simple. Split the conversation by `\n\n` (double newline) or every N characters (e.g., 500 characters). Store each chunk in `conversation_chunks`.

You are not doing anything smart with chunks yet. You are just getting used to the concept and making sure the table exists for Phase 2.

---

## Spring Boot Project Structure

```
src/main/java/com/yourname/llmmemory/
├── LlmMemoryApplication.java
│
├── conversation/
│   ├── Conversation.java              ← JPA entity
│   ├── ConversationChunk.java         ← JPA entity
│   ├── ConversationRepository.java    ← Spring Data JPA
│   ├── ConversationChunkRepository.java
│   ├── ConversationService.java       ← Business logic
│   └── ConversationController.java    ← REST endpoints
│
├── summarization/
│   ├── SummarizationService.java      ← Direct OpenAI HTTP call
│   └── OpenAiConfig.java              ← API key, base URL
│
└── shared/
    └── exception/
        └── GlobalExceptionHandler.java  ← @RestControllerAdvice for validation errors
```

---

## Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Lombok (optional but saves boilerplate)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
```

---

## application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/llmmemory
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

openai.api.key=${OPENAI_API_KEY}
openai.api.url=https://api.openai.com/v1/chat/completions
openai.model=gpt-4o-mini
```

---

## Things to Think About While Building

- How should you handle a conversation that is too long to summarize in one call? (You will solve this in Phase 2 with chunking + LangChain4j, but think about it now)
- What happens if the OpenAI call fails? Should you still store the conversation without a summary?
- What does "keyword search" miss that you wish it could do? (This will be your motivation for Phase 2 semantic search)

---

## Phase 1 Done When...

- [x] You can POST a raw conversation text and get it stored with a summary
- [x] You can GET a list of all conversations
- [x] You can search with a keyword and get relevant results back
- [x] You can DELETE a conversation by id (chunks go with it)
- [ ] You understand exactly what each class does and why it exists
- [ ] You can explain the data model and why there are two tables

---

## What Phase 1 Does NOT Do

- No URL fetching (Phase 2)
- No file upload (Phase 2)
- No embeddings (Phase 2)
- No semantic search (Phase 2)
- No agents (Phase 3)
- No Docker (Phase 4)

Resist the urge to add these now. Build the foundation solid first.
