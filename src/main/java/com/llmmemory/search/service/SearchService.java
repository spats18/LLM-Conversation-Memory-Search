package com.llmmemory.search.service;

import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.repository.ConversationRepository;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Service
public class SearchService {

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final int maxResults;
    private final double minScore;
    private final ConversationRepository conversationRepository;

    public SearchService(EmbeddingModel embeddingModel,
            PgVectorEmbeddingStore embeddingStore,
            @Value("${app.search.max-results}") int maxResults,
            @Value("${app.search.min-score}") double minScore,
            ConversationRepository conversationRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.conversationRepository = conversationRepository;
    }

    public List<Conversation> search(String query) {
        TextSegment textSegment = TextSegment.from(query);

        Response<Embedding> queryEmbedResponse = embeddingModel.embed(textSegment);
        Embedding queryEmbedding = queryEmbedResponse.content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> searchResults = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResults.matches();

        // A conversation is split into many chunks — multiple chunks from the same
        // conversation can all match the query.
        // We deduplicate by conversationId and keep only the best score
        // per conversation.
        // putIfAbsent works here because matches() is sorted highest score first,
        // so the first time we see a conversationId is already its best-scoring chunk.
        Map<UUID, Double> conversationScores = new HashMap<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            UUID conversationId = match.embedded().metadata().getUUID("conversationId");
            conversationScores.putIfAbsent(conversationId, match.score());
        }

        // One query for all matched conversations instead of N findById calls in the
        // loop.
        List<Conversation> matchingConversations = conversationRepository
                .findAllById(conversationScores.keySet());

        // findAllById does not guarantee return order — re-sort by score descending so
        // the most relevant conversation is first in the response.
        Collections.sort(matchingConversations,
                (a, b) -> conversationScores.get(b.getId())
                        .compareTo(conversationScores.get(a.getId())));
        return matchingConversations;
    }
}
