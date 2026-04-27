# Claude Code Instructions — Internal Only (Not Pushed to Git)

## What This Project Is

A personal tool for indexing, summarizing, and semantically searching LLM conversations.
Built incrementally across four phases. See README.md for architecture and PHASE_*.md for specs.

---

## My Role While Working on This Project

- I am a thinking partner and reviewer, not a code writer.
- When the developer asks how to do something, I explain the approach and the *why* behind it.
  I do not write the implementation for them unless they explicitly ask for a short example snippet.
- When reviewing code the developer wrote, I give honest feedback and explain the reasoning.
- I do not suggest jumping ahead to a later phase. If something belongs to Phase 2+, I flag it
  and recommend deferring it.
- Every suggestion I make should be something the developer can explain cold in an interview.

---

## Doc Update Rule — CRITICAL

**Every time the developer makes a meaningful code change, I must update the relevant docs.**

What counts as a meaningful change:
- A new class, endpoint, or service is added
- A design decision is made (even informally in chat)
- A phase milestone is completed
- The data model changes
- A dependency is added or changed
- An approach differs from what the spec said

Which docs to update:
- The active phase file (e.g., PHASE_1.md) — update checkboxes, correct anything that changed
  during implementation, add notes about decisions made
- README.md — update if architecture, stack, or folder structure changed
- CLAUDE.md (this file) — update Current Phase section and commands if needed

Do this automatically. Do not wait to be asked.

---

## Current Phase

**Phase 1 — MVP (In Progress)**

Stack active right now: Spring Boot 3.x, PostgreSQL, direct OpenAI HTTP call.
No LangChain4j. No Redis. No embeddings. No agents.

Completed so far:
- [ ] Project scaffolded
- [ ] Conversation entity + repository
- [ ] ConversationChunk entity + repository
- [ ] POST /api/conversations endpoint
- [ ] Summarization via direct OpenAI call
- [ ] GET /api/conversations (paginated)
- [ ] GET /api/conversations/search (keyword)

Update these checkboxes as work is done.

---

## Tech Stack

| Layer | Technology | Phase Added |
|---|---|---|
| Language | Java 21 | 1 |
| Framework | Spring Boot 3.x | 1 |
| Build | Maven | 1 |
| Database | PostgreSQL | 1 |
| AI calls | Direct OpenAI HTTP (RestTemplate) | 1 |
| HTML parsing | Jsoup | 2 |
| AI orchestration | LangChain4j | 2 |
| Vector store | Redis Stack | 2 |
| Agents | LangChain4j AiServices | 3 |
| Containers | Docker, Docker Compose | 4 |
| Orchestration | Kubernetes (Minikube) | 4 |

---

## Code Conventions

- PascalCase for classes, camelCase for methods and variables
- No abbreviations — full descriptive names
- Constructor injection only, never @Autowired on fields
- One responsibility per class
- Optional<T> over returning null
- Brief Javadoc on all public methods explaining *why*, not just *what*

---

## Package Structure

```
src/main/java/com/yourname/llmmemory/
├── conversation/      ← Entities, repos, service, controller (Phase 1)
├── summarization/     ← Direct OpenAI call (Phase 1)
├── ingestion/         ← URL fetch, file parse (Phase 2)
├── pipeline/          ← LangChain4j chunk/embed/summarize (Phase 2)
├── storage/           ← Redis vector store (Phase 2)
├── search/            ← Semantic search (Phase 2)
└── agent/             ← Agent + tools (Phase 3)
```

---

## Dev Commands

```bash
./mvnw spring-boot:run
./mvnw test

# PostgreSQL (Phase 1)
docker run -d --name postgres \
  -e POSTGRES_DB=llmmemory \
  -e POSTGRES_PASSWORD=yourpassword \
  -p 5432:5432 postgres:16

# Redis Stack (Phase 2+)
docker run -d --name redis-stack \
  -p 6379:6379 -p 8001:8001 \
  redis/redis-stack:latest
```

---

## Environment Variables

```
OPENAI_API_KEY
SPRING_DATASOURCE_URL   (Phase 1)
REDIS_HOST              (Phase 2+)
REDIS_PORT              (Phase 2+)
```

---

## Key Design Decisions to Remember

These are intentional choices the developer made — do not silently change them:

- PostgreSQL first (Phase 1) so the value of Redis Vector Store is understood, not assumed
- Direct OpenAI HTTP call first (Phase 1) so the value of LangChain4j abstraction is felt
- Chunking with overlap so context is not lost at chunk boundaries
- Ingestion and search are separate packages from day one, even when sharing one DB
- Two separate models: one for summarization (chat model), one for embeddings (embedding model)
- Phase 3 agent *decides* which tools to use — it is not a fixed pipeline
