package com.llmmemory.processing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.llmmemory.conversation.domain.entity.ConversationChunk;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;

    EmbeddingService(EmbeddingModel embeddingModel, PgVectorEmbeddingStore embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void embedChunks(List<ConversationChunk> chunks) {
        List<TextSegment> textSegments = new ArrayList<>();

        for (ConversationChunk chunk : chunks) {
            String rawContent = chunk.getContent();

            Metadata metadata = new Metadata();
            metadata.put("conversationId", chunk.getConversationId());
            metadata.put("chunkId", chunk.getId());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            TextSegment segment = TextSegment.from(rawContent, metadata);
            textSegments.add(segment);
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);

        TokenUsage tokenUsage = response.tokenUsage();
        log.info("Embedding token usage for {} chunks: {}", chunks.size(), tokenUsage);
        FinishReason finishReason = response.finishReason();
        log.info("Embedding finish reason: {}", finishReason);

        List<Embedding> embeddings = response.content();
        embeddingStore.addAll(embeddings, textSegments);
        log.info("Stored {} embeddings in pgvector", embeddings.size());
    }

    public void deleteEmbeddingsByConversationId(UUID conversationId) {
        embeddingStore.removeAll(new IsEqualTo("conversationId", conversationId));
        log.info("Deleted embeddings for conversation {}", conversationId);
    }
}
