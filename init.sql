-- Runs once when the Postgres data directory is first initialized (empty volume).
-- Enables the pgvector extension so the app can create vector(1536) columns.
-- The chunk_embeddings table is created by LangChain4j's PgVectorEmbeddingStore on app startup.
-- The conversations table and conversation_chunks table are created by Hibernate (ddl-auto=update).
--
-- NOTE: The tsvector generated column and GIN index on conversations.search_vector are NOT
-- created by Hibernate. After the very first `docker compose up`, run these once via psql:
--
--   docker compose exec postgres psql -U postgres -d llmmemory -c \
--     "ALTER TABLE conversations ADD COLUMN IF NOT EXISTS search_vector tsvector
--      GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(raw_content,''))) STORED;"
--
--   docker compose exec postgres psql -U postgres -d llmmemory -c \
--     "CREATE INDEX IF NOT EXISTS conversations_search_vector_idx ON conversations USING GIN (search_vector);"
--
-- This is a known limitation — Flyway will manage the full schema in a later phase.

CREATE EXTENSION IF NOT EXISTS vector;
