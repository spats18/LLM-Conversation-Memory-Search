# Phase 3 — Docker + Docker Compose

## Goal

Package and deploy the full stack — Spring Boot app + PostgreSQL (with pgvector) — using Docker for containerisation and Docker Compose for local orchestration.

---

## What Was Built

### Dockerfile

Multi-stage build: Stage 1 compiles with the JDK and produces a fat jar. Stage 2 starts fresh from a JRE-only base image and copies only the jar. The final image contains no compiler, no Gradle cache, no source code — roughly 150MB vs 500MB+ for a single-stage build.

The container runs as a non-root user (`appuser`) to limit blast radius if the container is ever compromised.

Layer ordering is deliberate: build files (`build.gradle.kts`, `gradlew`) are copied before source code so that Gradle dependency downloads are cached and not re-run on every source change.

### Docker Compose

Two services declared in `docker-compose.yml`:

- **postgres** — `pgvector/pgvector:pg16`, the official Postgres image with the pgvector extension pre-installed. Exposes port 5432 to the host so TablePlus can connect. A named volume (`postgres_data`) persists data across container restarts.
- **app** — built from the Dockerfile. Port 8080 mapped to host. Env vars override the datasource URL and DB host so the app reaches `postgres` (the service name on the Docker internal network) instead of `localhost`.

Key wiring decisions:
- `depends_on: condition: service_healthy` — app waits until the postgres healthcheck (`pg_isready`) passes before starting. Without this the app crashes on startup because Postgres hasn't finished initialising.
- `SPRING_DATASOURCE_URL` and `APP_DB_HOST` both overridden via env vars — Spring Boot's relaxed binding maps OS env vars to property names at runtime, no code change needed.
- `OPENAI_API_KEY` and `POSTGRES_PASSWORD` come from the shell at runtime — never hardcoded in the file.

### init.sql

Runs once when the Postgres data volume is first created. Enables the `vector` extension so the app can create `vector(1536)` columns. The `chunk_embeddings` table is created by LangChain4j's `PgVectorEmbeddingStore` on app startup. The `conversations` and `conversation_chunks` tables are created by Hibernate (`ddl-auto=update`).

The `tsvector` generated column and GIN index on `conversations.search_vector` are applied once manually after first startup — Hibernate cannot create generated columns. This is the known limitation; Flyway will manage the full schema in a later phase.

---

## Why Kubernetes Was Skipped

Kubernetes solves availability and independent scaling across a cluster of machines. For this project — one user, one machine, no availability requirement — it adds no product value. Docker Compose is the correct tool at this scale.

The honest engineering call: Kubernetes complexity is only justified when you have at least one of:
- Multiple users with concurrent traffic
- An availability requirement (no downtime on deploy or crash)
- Services with genuinely different scaling profiles that need to scale independently

This project has none of those. Adding Kubernetes manifests would have been resume-driven development — complexity added for appearance, not function.

**The interview answer:** *"Ingestion and search have different resource profiles — ingestion is CPU-heavy and bursty, search is lightweight and frequent. If this were a multi-user service I'd deploy them as separate Kubernetes Deployments and scale them independently. At single-user scale, that adds complexity with no payoff. Docker Compose is sufficient and Kubernetes would be over-engineering."*

Knowing when not to use a tool is as important as knowing how to use it.

---

## How to Run

```bash
# Export secrets (required every new terminal session)
export POSTGRES_PASSWORD=yourpassword
export OPENAI_API_KEY=sk-...

# Start both services
docker compose up

# First startup only — add tsvector column and GIN index
docker compose exec postgres psql -U postgres -d llmmemory -c \
  "ALTER TABLE conversations ADD COLUMN IF NOT EXISTS search_vector tsvector \
   GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(raw_content,''))) STORED;"

docker compose exec postgres psql -U postgres -d llmmemory -c \
  "CREATE INDEX IF NOT EXISTS conversations_search_vector_idx ON conversations USING GIN (search_vector);"
```

App is available at `localhost:8080`. Postgres is available at `localhost:5432` for TablePlus.
