# Phase 2 — Real Ingestion: URL Fetch + File Upload + LangChain4j + Redis

## Goal

Replace the manual pieces from Phase 1 with a **real, production-grade ingestion pipeline**.

By the end of Phase 2, the system:
- Accepts a Claude share URL → fetches and parses it automatically
- Accepts an exported Claude JSON file → parses it correctly
- Uses LangChain4j to orchestrate: chunk → summarize → embed
- Stores embeddings in Redis Vector Store
- Returns semantically relevant results — not just keyword matches

---

## Why This Order Matters

Phase 1 was built without LangChain4j or Redis on purpose. The manual OpenAI HTTP call is verbose, and keyword search has obvious gaps. Phase 2 replaces both with purpose-built tools — and the motivation for each is concrete rather than theoretical.

---

## Part A: Input Sources

### Source 1: Claude Share URL

When you share a Claude conversation, it becomes publicly accessible at a URL like:
`https://claude.ai/share/some-unique-id`

Your app will:
1. Accept that URL from the user
2. Make an HTTP GET request to fetch the page HTML
3. Parse the HTML to extract the conversation turns (User / Assistant messages)
4. Feed the parsed text into the ingestion pipeline

**Technology to use:** `Jsoup` — a Java HTML parser. Simple, battle-tested.

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

**Important caveat:** Claude's HTML structure can change. Your parser will be fragile — that is expected and honest. You should document this in your code. The interviewer will respect that you understood the tradeoff.

**What to extract:**
- The conversation title (if present)
- Each message turn: role (User/Assistant) and content text
- URL and fetch timestamp (for your records)

### Source 2: Exported Claude JSON

Claude lets you export your conversations as a JSON file. The structure looks roughly like:

```json
{
  "title": "Conversation about Spring Boot",
  "created_at": "2025-04-01T10:00:00Z",
  "messages": [
    {
      "role": "user",
      "content": "How do I set up Spring Boot?"
    },
    {
      "role": "assistant",
      "content": "First, go to start.spring.io..."
    }
  ]
}
```

Your app will accept a file upload (multipart/form-data), parse this JSON using Jackson (already in Spring Boot), and feed it into the same ingestion pipeline.

Both sources should produce the same internal `ParsedConversation` object. The pipeline does not care where the conversation came from.

```java
public class ParsedConversation {
    private String title;
    private String sourceUrl;       // null if from file
    private String sourceType;      // "url" or "file"
    private List<ConversationTurn> turns;
}

public class ConversationTurn {
    private String role;            // "user" or "assistant"
    private String content;
}
```

---

## Part B: LangChain4j

### What LangChain4j Is

LangChain4j is the Java port of LangChain. It provides:
- Abstractions for LLM calls (so you can swap OpenAI for Ollama with one line)
- Document chunking (text splitters)
- Embedding model wrappers
- Vector store integrations (including Redis)
- Chain and agent building blocks

### Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.32.0</version>  <!-- check for latest -->
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.32.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-redis</artifactId>
    <version>0.32.0</version>
</dependency>
```

### The Ingestion Pipeline with LangChain4j

Replace your manual `SummarizationService` with a proper LangChain4j pipeline:

```
ParsedConversation
       │
       ▼
  DocumentLoader         ← Convert ParsedConversation → LangChain4j Document
       │
       ▼
  TextSplitter           ← Split into chunks (e.g., 500 tokens with 50 token overlap)
       │
       ▼
  SummarizationChain     ← For each chunk: call LLM to summarize
       │
       ▼
  EmbeddingModel         ← For each chunk+summary: generate vector embedding
       │
       ▼
  RedisVectorStore       ← Store chunk, summary, embedding, metadata
```

**Why chunking overlap?** If you split at a hard boundary, context gets cut. With 50-token overlap, each chunk shares some content with its neighbors so nothing is lost at the seam.

### Model Configuration (Swappable Design)

```java
// OpenAI version
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o-mini")
    .build();

EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-3-small")
    .build();

// Ollama version (swap without changing business logic)
ChatLanguageModel model = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1:8b")
    .build();

EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("nomic-embed-text")
    .build();
```

This is the core value of LangChain4j's model abstraction — the underlying model is a configuration detail, not a structural dependency.

---

## Part C: Redis Vector Store

### Why Redis Stack

Redis Stack includes RediSearch and RedisJSON, which add native vector similarity search on top of the Redis data structures you already know. It's self-hostable, works well locally and in Kubernetes, and LangChain4j has a first-class integration for it.

### Running Redis Locally

```bash
docker run -d --name redis-stack \
  -p 6379:6379 \
  -p 8001:8001 \
  redis/redis-stack:latest
```

Port 8001 gives you RedisInsight — a web UI to inspect your data. Use it to see your vectors being stored.

### What Gets Stored in Redis

Each conversation chunk becomes a Redis document with:

```
{
  "chunk_id": "uuid",
  "conversation_id": "uuid",
  "conversation_title": "My Spring Boot conversation",
  "source_url": "https://claude.ai/share/...",
  "chunk_content": "The raw chunk text",
  "summary": "A summary of this chunk",
  "embedding": [0.123, -0.456, ...],   ← 1536 dimensions for text-embedding-3-small
  "created_at": "2025-04-27T10:00:00Z"
}
```

### Semantic Search

When the user types a query:

1. Embed the query using the same embedding model
2. Run a KNN (k-nearest neighbors) similarity search in Redis
3. Return the top N most similar chunks with their conversation metadata

```java
// Pseudocode
List<Float> queryEmbedding = embeddingModel.embed(userQuery);
List<SearchResult> results = redisVectorStore.findSimilar(queryEmbedding, topK = 5);
```

The difference from keyword search: if the user stored a conversation about "machine learning model training" and searches for "neural network optimization", semantic search finds it. Keyword search would not.

---

## Updated API Endpoints

### POST /api/conversations/ingest-url
```json
{
  "url": "https://claude.ai/share/abc123"
}
```

### POST /api/conversations/ingest-file
Multipart form upload of the exported JSON file.

### GET /api/conversations/search?q=your+query
Now uses semantic search instead of keyword search. Returns ranked results with relevance scores.

### GET /api/conversations
Same as Phase 1. Lists all stored conversations.

---

## Updated Project Structure

```
src/main/java/com/yourname/llmmemory/
├── LlmMemoryApplication.java
│
├── ingestion/
│   ├── IngestionService.java          ← Orchestrates the full pipeline
│   ├── UrlFetcherService.java         ← Fetches and parses Claude share URLs
│   ├── FileParserService.java         ← Parses exported Claude JSON
│   ├── ParsedConversation.java        ← Common internal format
│   └── ConversationTurn.java
│
├── pipeline/
│   ├── ChunkingService.java           ← LangChain4j text splitter wrapper
│   ├── SummarizationService.java      ← LangChain4j LLM chain wrapper
│   └── EmbeddingService.java          ← LangChain4j embedding model wrapper
│
├── storage/
│   └── RedisVectorStoreService.java   ← Store and retrieve from Redis
│
├── search/
│   └── SearchService.java             ← Embed query, search Redis, rank results
│
└── api/
    └── ConversationController.java    ← Updated REST endpoints
```

---

## New Dependencies to Add

```xml
<!-- Jsoup for HTML parsing -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>

<!-- LangChain4j core -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.32.0</version>
</dependency>

<!-- LangChain4j OpenAI -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.32.0</version>
</dependency>

<!-- LangChain4j Redis -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-redis</artifactId>
    <version>0.32.0</version>
</dependency>

<!-- Redis client -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

---

## Phase 2 Done When...

- [ ] POST a Claude share URL → conversation is fetched, parsed, chunked, summarized, embedded, stored in Redis
- [ ] POST an exported JSON file → same pipeline, different input
- [ ] Semantic search returns results that keyword search would miss
- [ ] Swapping from OpenAI to Ollama requires only a config change
- [ ] RedisInsight shows stored embeddings correctly

---

## What Phase 2 Does NOT Do

- No agent decision-making (Phase 3)
- No Docker Compose setup (Phase 4)
- No Kubernetes (Phase 4)
