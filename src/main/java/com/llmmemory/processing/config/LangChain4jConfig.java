package com.llmmemory.processing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class LangChain4jConfig {

    private final String apiKey;
    private final String chatModelName;
    private final String embeddingModelName;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public LangChain4jConfig(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.model}") String chatModelName,
            @Value("${openai.api.embedding-model}") String embeddingModelName,
            @Value("${app.db.host}") String dbHost,
            @Value("${app.db.port}") int dbPort,
            @Value("${app.db.name}") String dbName,
            @Value("${app.db.user}") String dbUser,
            @Value("${app.db.password}") String dbPassword) {
        this.apiKey = apiKey;
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    // Used by SummarizationService to summarize raw conversation content
    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .build();
    }

    // Used by EmbeddingService to convert chunk text into vectors stored in pgvector (Phase 2)
    // Phase 5 swaps the store to Redis Stack — the model itself does not change
    // Kept separate from chatLanguageModel — different OpenAI endpoint, different response shape
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }

    // Creates the pgvector embedding store — LangChain4j manages the chunk_embeddings table.
    // dimension(1536) must match text-embedding-3-small's fixed output size.
    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbName)
                .user(dbUser)
                .password(dbPassword)
                .table("chunk_embeddings")
                .dimension(1536)
                .build();
    }
}
