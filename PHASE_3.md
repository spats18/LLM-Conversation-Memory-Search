# Phase 3 — Agentic Layer

## Goal

Add a real **agent** that can autonomously decide which tools to use and when, based on the user's question.

This is the phase that makes the project genuinely "agentic" — not just a pipeline that runs steps in sequence, but a system that **reasons** about what to do next.

---

## What Changes in Phase 3

In Phase 1 and 2, the system is reactive:
- User submits a conversation → pipeline runs → done
- User searches → results come back → done

In Phase 3, the system becomes **proactive and autonomous**:
- User asks a question → the agent *decides* whether to search memory first, summarize something, or answer directly
- The agent has a set of tools available and chooses which ones to call
- The agent can chain tool calls: search → retrieve chunk → summarize further → answer

This is the pattern that makes LLM-powered systems genuinely useful, and it is what most job descriptions mean when they say "agentic AI."

---

## What Is an Agent (Concretely)

An agent is an LLM that has been given:
1. A set of **tools** (functions it can call)
2. A **goal** (the user's question)
3. A **loop**: it thinks, calls a tool, observes the result, thinks again, calls another tool if needed, until it has an answer

The LLM decides *which* tool to call and *what arguments* to pass. Your code executes the tool and returns the result. The LLM sees the result and decides what to do next.

LangChain4j implements this with its `AiServices` and `@Tool` annotation pattern.

---

## The Agent You Will Build

### Name: Conversation Memory Agent

The agent answers questions by first checking if it has relevant memory from your past conversations.

**Example interaction:**

```
User: "What was that Spring Boot setup process I was asking about last week?"

Agent thinks: I should search the conversation memory for Spring Boot setup.
Agent calls: search_conversations("Spring Boot setup")
Agent sees: 3 relevant chunks returned
Agent thinks: I have enough context. Let me compose an answer.
Agent responds: "Based on a conversation from April 15th titled 'Setting up Spring Boot',
                 you were asking about initializing a project from start.spring.io.
                 The key steps discussed were..."
```

### Another example:

```
User: "Do I have any past conversations about Redis?"

Agent thinks: I should search for Redis in conversation memory.
Agent calls: search_conversations("Redis")
Agent sees: 2 conversations found
Agent responds: "You have 2 conversations mentioning Redis:
                 1. 'LLM Memory Project' (April 27) — discussed Redis Vector Store for embeddings
                 2. 'Caching strategies' (March 10) — discussed Redis as a cache"
```

---

## The Tools

Define these as Java methods annotated with `@Tool` in LangChain4j:

### Tool 1: `search_conversations`

```java
@Tool("Search past conversations by semantic similarity to the query")
public List<SearchResult> searchConversations(
    @P("The search query describing what to look for") String query,
    @P("Maximum number of results to return") int topK
) {
    return searchService.semanticSearch(query, topK);
}
```

### Tool 2: `get_conversation_detail`

```java
@Tool("Get the full content of a specific conversation by its ID")
public ConversationDetail getConversationDetail(
    @P("The conversation ID") String conversationId
) {
    return storageService.getById(conversationId);
}
```

### Tool 3: `list_recent_conversations`

```java
@Tool("List the most recently indexed conversations")
public List<ConversationSummary> listRecentConversations(
    @P("How many recent conversations to return") int count
) {
    return storageService.getRecent(count);
}
```

---

## LangChain4j Agent Setup

```java
// Define the agent interface
interface MemoryAgent {
    String chat(String userMessage);
}

// Build the agent with tools
MemoryAgent agent = AiServices.builder(MemoryAgent.class)
    .chatLanguageModel(chatModel)
    .tools(new ConversationTools(searchService, storageService))
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .build();
```

The `chatMemory` is important: the agent remembers the conversation *within a session*. This means you can have multi-turn interactions:

```
User: "What have I stored about databases?"
Agent: [searches, returns results about Redis, PostgreSQL conversations]

User: "Tell me more about the Redis one"
Agent: [remembers context, calls get_conversation_detail for the Redis conversation]
```

---

## New API Endpoint

### POST /api/agent/chat

```json
Request:
{
  "message": "What was I learning about last week?",
  "sessionId": "optional-session-id-for-multi-turn"
}

Response:
{
  "response": "Based on your indexed conversations from last week, you were exploring...",
  "toolsUsed": ["search_conversations"],
  "conversationsReferenced": ["uuid1", "uuid2"]
}
```

The `sessionId` allows multi-turn conversations with the agent (it remembers what was said earlier in the session).

---

## System Prompt for the Agent

The system prompt tells the agent how to behave:

```
You are a personal conversation memory assistant. 
You help the user recall and explore their past LLM conversations.

You have access to the user's indexed conversation history.
Always search the conversation memory before answering questions about past discussions.
When referencing a past conversation, mention its title and approximate date.
If you cannot find relevant information in memory, say so clearly — do not make things up.
Be concise and helpful. The user is a developer.
```

---

## Agent vs Pipeline

| Fixed Pipeline (Phase 2) | Agent (Phase 3) |
|---|---|
| Always runs the same steps | Decides which steps to run |
| One tool: search | Multiple tools: search, get detail, list recent |
| Single pass | Multi-step reasoning loop |
| No session memory | Remembers conversation context within a session |
| Deterministic | LLM decides what to do |

---

## Updated Project Structure

```
src/main/java/com/yourname/llmmemory/
├── ... (all Phase 1 and 2 files) ...
│
└── agent/
    ├── MemoryAgent.java               ← LangChain4j AiServices interface
    ├── ConversationTools.java         ← @Tool annotated methods
    ├── AgentService.java              ← Manages agent instances per session
    └── AgentController.java           ← POST /api/agent/chat endpoint
```

---

## Session Management Consideration

Each user session should have its own agent with its own `ChatMemory`. You need to think about:

- How do you identify a session? (Could be as simple as a UUID the client generates)
- How long do you keep session memory? (In memory is fine for now — it resets on restart)
- What happens when the session is too long? (`MessageWindowChatMemory` handles this by dropping old messages)

This is a good engineering discussion topic — you made a deliberate decision about session management and you know why.

---

## Phase 3 Done When...

- [ ] POST /api/agent/chat works and the agent uses tools to answer questions
- [ ] The agent correctly searches memory when asked about past conversations
- [ ] Multi-turn conversation works within a session
- [ ] The system prompt constrains the agent to only discuss indexed conversations

---

## What Phase 3 Does NOT Do

- No Docker Compose (Phase 4)
- No Kubernetes (Phase 4)
- No UI — still all REST API (a simple UI is optional but not required for the portfolio)
