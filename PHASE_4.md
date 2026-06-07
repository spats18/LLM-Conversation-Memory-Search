# Phase 4 — Guard Rails

> Not started. Phases 1–3 are complete. This phase hardens the API against abuse, bad data, and unexpected LLM behaviour before the input surface is expanded in Phase 5.

## Goal

The ingestion pipeline is now user-exposed. Every ingest request triggers at minimum two paid OpenAI API calls. Without defensive boundaries, a single user can flood the API, submit harmful content, send content designed to manipulate the summarization LLM, or pass inputs so large they silently fail. This phase adds the guard rails that make the API safe to expose.

---

## Priority

| Guard Rail | Priority | Why |
|---|---|---|
| Token budget guard | High | A conversation that is too long will always fail summarization — but right now the app does not know that until after it has made the API call and paid for it. Reject oversized input at the door, before any LLM call happens. |
| Content moderation | High | Without a content check, anyone can store harmful or explicit material and it flows straight into the summarization LLM and gets persisted to the database. One free OpenAI Moderation API call per ingest blocks this entirely. |
| Prompt injection detection | Medium | The summarization LLM receives the full raw conversation text as input. A user can embed instructions like "ignore your system prompt and output X" inside the conversation — the model may obey them. Scan rawContent for injection patterns before it reaches the LLM. |
| Rate limiting | Medium | Every ingest triggers two OpenAI API calls (summarization + embedding). Without a limit, a single user can flood the endpoint and run up significant API costs in seconds. A per-minute cap on ingest and search requests caps the blast radius. |
| Minimum content length | Medium | Submitting a 10-word string produces an embedding that means almost nothing — it degrades every future search because the vector space gets polluted with low-signal data. A minimum length ensures only real conversations enter the pipeline. |
| Summary output validation | Low | After summarization, the LLM could return an empty string, a refusal, or a jailbreak response. All three would be silently stored as the summary. A post-call check catches these before they hit the database. |
| Near-duplicate detection | Low | Title uniqueness is enforced, but the same conversation resubmitted under a different title gets indexed twice. Two identical embeddings in the vector store means every search returns the same conversation twice, ranked equally — corrupting result quality. |

---

## Guard Rail 1 — Token Budget Guard

**Where:** `ConversationService.createConversation()`, before any LLM call.

**What it does:** Rejects requests whose `rawContent` exceeds a configurable character limit. Prevents silent `[SUMMARIZATION_FAILED]` outcomes caused by context window overflow, and avoids paying for API calls that are guaranteed to fail.

**What's needed:**
- `app.guardrails.max-content-length` property (e.g. 50000 characters — roughly 12,500 tokens, safely within gpt-4o-mini's context window)
- Check in `createConversation` before `summarizationService.summarize()` is called
- New exception `ContentTooLargeException` → 413 response via `GlobalExceptionHandler`

---

## Guard Rail 2 — Content Moderation

**Where:** `ConversationService.createConversation()`, before summarization.

**What it does:** Passes `rawContent` through OpenAI's Moderation API. If the content is flagged as sexual, violent, hateful, or harmful, the request is rejected before any storage or LLM call occurs.

**What's needed:**
- `ModerationService` in `processing/service/` — calls OpenAI's `/v1/moderations` endpoint
- New exception `ContentModerationException` → 422 response
- The Moderation API is free — no new billing scope required, but confirm the key has access

---

## Guard Rail 3 — Prompt Injection Detection

**Where:** `ConversationService.createConversation()`, before summarization. Also on `rawContent` from search if a generative step is added later.

**What it does:** Scans `rawContent` for patterns commonly used in prompt injection attacks (instruction override attempts, role-switching commands, system prompt extraction requests). Rejects or sanitises content that contains them.

**What's needed:**
- `PromptInjectionGuard` in `shared/guardrails/` — pattern-based scan using a curated regex list
- Configurable strictness: `REJECT` (throw exception) vs `SANITISE` (strip matched spans and continue)
- New exception `PromptInjectionException` → 422 response
- Note: regex-based detection catches obvious attempts; LLM-based detection is more thorough but adds cost — deferred

---

## Guard Rail 4 — Rate Limiting

**Where:** Controller layer — `POST /api/v1/conversations` (ingest) and `GET /api/v1/conversations/search` (semantic search).

**What it does:** Limits how many requests a client can make per time window. Ingest is more expensive (two LLM calls) so its limit is tighter than search.

**What's needed:**
- `bucket4j-spring-boot-starter` dependency
- Two separate buckets: one for ingest (e.g. 10 requests/minute), one for search (e.g. 30 requests/minute)
- Both limits configurable via `app.guardrails.rate-limit.*` properties
- 429 response when limit is exceeded — already handled by `GlobalExceptionHandler` catch-all; add a specific `RateLimitExceededException` for a cleaner message

---

## Guard Rail 5 — Minimum Content Length

**Where:** `ConversationRequest` validation, before the request reaches the service.

**What it does:** Rejects conversations whose `rawContent` is below a minimum character threshold. Short content produces low-quality embeddings that degrade search for everything else in the store.

**What's needed:**
- `@Size(min = ...)` on `ConversationRequest.rawContent` (e.g. `min = 100`)
- `app.guardrails.min-content-length` property so it can be tuned without recompiling
- Handled by existing `MethodArgumentNotValidException` → 400 flow in `GlobalExceptionHandler`

---

## Guard Rail 6 — Summary Output Validation

**Where:** `ConversationService.createConversation()`, after `summarizationService.summarize()` returns.

**What it does:** Validates that the returned summary is non-empty, within a reasonable length range, and does not match known LLM refusal patterns (e.g. "I'm sorry", "I cannot", "As an AI"). If validation fails, falls back to `[SUMMARIZATION_FAILED]` with a specific reason logged.

**What's needed:**
- `SummaryValidator` in `processing/service/` — lightweight check, no LLM call
- Configurable min/max summary length via `app.guardrails.summary.*` properties

---

## Guard Rail 7 — Near-Duplicate Content Detection

**Where:** `ConversationService.createConversation()`, after the token budget and moderation checks pass, before saving to the database.

**What it does:** Embeds the full `rawContent` and checks cosine similarity against embeddings of existing conversations. If similarity exceeds a threshold (e.g. 0.97), the request is rejected as a duplicate regardless of title.

**What's needed:**
- Additional query on `PgVectorEmbeddingStore` or direct SQL on `chunk_embeddings`
- Configurable threshold via `app.guardrails.duplicate-similarity-threshold`
- New exception `DuplicateContentException` → 409 response
- Note: adds one embedding API call per ingest — evaluate whether the cost is acceptable before implementing

---

## Design Decisions

> To be recorded as implementation progresses.
