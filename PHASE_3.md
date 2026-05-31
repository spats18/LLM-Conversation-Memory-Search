# Phase 3 — Agentic Layer

> **Status: 🔲 Not started.**

## Goal

Phase 3 adds an agent that decides which tools to call based on the user's question, rather than running a fixed pipeline. The agent can chain tool calls — search → retrieve → summarize further — within a single response.

---

## What Changes

Phase 1 and 2 are reactive: a request arrives, a fixed set of steps runs, a response is returned. Phase 3 introduces a reasoning loop: the agent receives a question, decides whether to search memory, retrieve a specific conversation, or answer directly, observes the result, and calls further tools if needed.

---

## The Agent

### Conversation Memory Agent

Answers questions by searching indexed conversation history. Built using LangChain4j `AiServices` — the `@Tool`-annotated methods are what the LLM can invoke.

```java
interface MemoryAgent {
    String chat(String userMessage);
}

MemoryAgent agent = AiServices.builder(MemoryAgent.class)
    .chatLanguageModel(chatModel)
    .tools(new ConversationTools(searchService, storageService))
    .chatMemory(MessageWindowChatMemory.builder()
        .maxMessages(10)
        .chatMemoryStore(RedisChatMemoryStore.builder()
            .host("localhost")
            .port(6379)
            .build())
        .build())
    .build();
```

`chatMemory` gives the agent session context so multi-turn conversations work within a session. Chat history is stored in Redis rather than in-process memory — sessions survive app restarts and are shared across instances. At single-user scale this is not strictly necessary, but it demonstrates Redis in its natural role as a session state store and works correctly under Phase 4's multi-replica Kubernetes deployment.

---

## Tools

### `search_conversations`

```java
@Tool("Search past conversations by semantic similarity to the query")
public List<SearchResult> searchConversations(
    @P("The search query") String query,
    @P("Maximum results to return") int topK
) {
    return searchService.semanticSearch(query, topK);
}
```

### `get_conversation_detail`

```java
@Tool("Get the full content of a specific conversation by its ID")
public ConversationDetail getConversationDetail(
    @P("The conversation ID") String conversationId
) {
    return storageService.getById(conversationId);
}
```

### `list_recent_conversations`

```java
@Tool("List the most recently indexed conversations")
public List<ConversationSummary> listRecentConversations(
    @P("How many recent conversations to return") int count
) {
    return storageService.getRecent(count);
}
```

---

## System Prompt

```
You are a personal conversation memory assistant.
You help the user recall and explore their past LLM conversations.

Always search the conversation memory before answering questions about past discussions.
When referencing a past conversation, mention its title and approximate date.
If you cannot find relevant information in memory, say so — do not fabricate.
Be concise. The user is a developer.
```

---

## API Endpoint

### POST /api/v1/agent/chat

**Request:**
```json
{
  "message": "What was I learning about last week?",
  "sessionId": "optional-uuid-for-multi-turn"
}
```

**Response:**
```json
{
  "response": "Based on your indexed conversations from last week, you were exploring...",
  "toolsUsed": ["search_conversations"],
  "conversationsReferenced": ["uuid1", "uuid2"]
}
```

`sessionId` is client-generated. Session memory is stored in Redis and survives restarts. `MessageWindowChatMemory` drops oldest messages when the window fills.

---

## Fixed Pipeline vs Agent

| Phase 2 Pipeline | Phase 3 Agent |
|---|---|
| Fixed steps, always the same order | Decides which steps to run |
| One tool: semantic search | search, get detail, list recent |
| Single pass | Multi-step reasoning loop |
| No session memory | Session memory within a session |

---

## Package Structure

```
src/main/java/com/llmmemory/
└── agent/
    ├── MemoryAgent.java               ← LangChain4j AiServices interface
    ├── ConversationTools.java         ← @Tool-annotated methods
    ├── AgentService.java              ← Session-scoped agent instances
    └── AgentController.java           ← POST /api/v1/agent/chat
```

---

## Redis Dependency

Phase 3 introduces Redis for the first time. Run it locally alongside the existing stack:

```bash
docker run -d --name redis \
  -p 6379:6379 \
  redis/redis-stack:latest
```

Dependency (community module, versioned outside the BOM):
```kotlin
implementation("dev.langchain4j:langchain4j-redis:1.0.0-beta2")
```

---

## Data Store Responsibilities

| Store | Role |
|---|---|
| Postgres + pgvector | Conversations, chunks, embeddings — permanent data |
| Redis | Agent session chat history — session-scoped state |

Two stores, two genuinely separate jobs. pgvector owns the knowledge base; Redis owns the live conversation context.

---

## Phase 3 Done When...

- [ ] POST /api/v1/agent/chat responds using tools from indexed memory
- [ ] Multi-turn conversation works within a session
- [ ] Session history persists in Redis across app restarts
- [ ] The system prompt prevents the agent from answering outside its indexed data
- [ ] The agent correctly chains tool calls (e.g. search → get detail)

---

## What Phase 3 Does NOT Do

- No Docker Compose (Phase 4)
- No Kubernetes (Phase 4)
- No UI — REST API only
