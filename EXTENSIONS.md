# Extensions — Post-MVP

Features deferred after the core four phases are complete. Pick these up if time allows.

---

## Claude Share URL Ingestion

`POST /api/v1/conversations/ingest-url` — accepts a Claude share URL (e.g. `https://claude.ai/share/some-id`), fetches the page via Jsoup, parses the HTML to extract the title and conversation turns, and feeds a `ParsedConversation` into the existing ingestion pipeline.

**Why deferred:** The HTML scraping is inherently fragile — Claude's share page structure is undocumented and can change at any time. The file upload path (Phase 2 MVP) demonstrates the same pipeline with zero scraping risk.

**What's needed:**
- `UrlFetcherService` in `ingestion/` — Jsoup HTTP GET + HTML parse
- Add `sourceUrl` field back to `ParsedConversation`
- New endpoint wired into the existing `IngestionService`
- Dependency already present: `implementation("org.jsoup:jsoup:1.17.2")`
