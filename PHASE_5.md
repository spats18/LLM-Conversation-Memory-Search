# Phase 5 — Input Sources

> Not started. Phases 1–4 must be complete first. This phase extends the input layer beyond raw text paste.

## Goal

Add structured ingestion sources on top of the existing pipeline — currently only raw text paste is supported.

---

## Source 1: Exported Claude JSON

`POST /api/v1/conversations/ingest-file` accepts a multipart upload of an exported Claude JSON file.

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
- `IngestionService` — flattens turns into text, hands off to the existing pipeline

---

## Source 2: Claude Share URL

`POST /api/v1/conversations/ingest-url` accepts a Claude share URL. `UrlFetcherService` fetches the page via Jsoup, parses the HTML to extract title and turns, and feeds the existing pipeline.

**What's needed:**
- `UrlFetcherService` in `ingestion/` — Jsoup HTTP GET + HTML parse
- Jsoup dependency: `implementation("org.jsoup:jsoup:1.17.2")`

Note: Claude's share page HTML structure is undocumented and may change without notice.
