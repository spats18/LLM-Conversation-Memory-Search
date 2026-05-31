package com.llmmemory.pipeline.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    private final String apiKey;
    private final String chatModelName;
    private final String embeddingModelName;

    public LangChain4jConfig(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.model}") String chatModelName,
            @Value("${openai.api.embedding-model}") String embeddingModelName) {
        this.apiKey = apiKey;
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
    }

    // Used by SummarizationService to generate per-chunk summaries
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
}
