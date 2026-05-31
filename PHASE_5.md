# Phase 5 — Additional Input Sources

> **Status: 🔲 Post-MVP. Start after Phase 4 is complete.**

## Goal

Add structured ingestion sources on top of the existing pipeline. Phase 2 feeds the LangChain4j pipeline via the raw text paste endpoint — Phase 5 adds two richer input paths that produce the same downstream result.

---

## Source 1: Exported Claude JSON

`POST /api/v1/conversations/ingest-file` accepts a multipart upload of an exported Claude JSON file. Jackson (already on the classpath) deserializes it into a `ParsedConversation`, which feeds the existing pipeline.

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

---

## Source 2: Claude Share URL

`POST /api/v1/conversations/ingest-url` accepts a Claude share URL (e.g. `https://claude.ai/share/some-id`). `UrlFetcherService` issues an HTTP GET via Jsoup, parses the HTML to extract the title and turns, and feeds the same pipeline.

The HTML parser is intentionally fragile — Claude's share page structure is undocumented and can change at any time. This is documented in the code as a known tradeoff.

**What's needed:**
- `UrlFetcherService` in `ingestion/` — Jsoup HTTP GET + HTML parse
- Dependency already present: `implementation("org.jsoup:jsoup:1.17.2")`

---

## Phase 5 Done When...

- [ ] POST an exported JSON file → parsed, chunked, embedded, stored in Redis
- [ ] POST a Claude share URL → fetched, parsed, chunked, embedded, stored in Redis
