package com.llmmemory.conversation.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.domain.entity.ConversationChunk;
import com.llmmemory.conversation.repository.ConversationChunkRepository;
import com.llmmemory.conversation.repository.ConversationRepository;
import com.llmmemory.processing.service.ChunkingService;
import com.llmmemory.processing.service.EmbeddingService;
import com.llmmemory.processing.service.SummarizationService;
import com.llmmemory.search.service.SearchService;
import com.llmmemory.processing.exception.SummarizationException;
import com.llmmemory.shared.exception.ConversationNotFoundException;
import com.llmmemory.shared.exception.DuplicateTitleException;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer for managing conversations.
 * Handles the orchestration of
 * - creating a conversation,
 * - including summarization, chunking, and embedding.
 * - deleting conversations and their associated chunks.
 */
@Service
@Slf4j
public class ConversationService {
  private final ChunkingService chunkingService;
  private final SummarizationService summarizationService;
  private final ConversationRepository conversationRepository;
  private final ConversationChunkRepository conversationChunkRepository;
  private final EmbeddingService embeddingService;
  private final SearchService searchService;

  public ConversationService(
      ChunkingService chunkingService,
      SummarizationService summarizationService,
      ConversationRepository conversationRepository,
      ConversationChunkRepository conversationChunkRepository,
      EmbeddingService embeddingService,
      SearchService searchService) {
    this.chunkingService = chunkingService;
    this.summarizationService = summarizationService;
    this.conversationRepository = conversationRepository;
    this.conversationChunkRepository = conversationChunkRepository;
    this.embeddingService = embeddingService;
    this.searchService = searchService;
  }

  @Transactional
  public Conversation createConversation(String title, String source, String rawContent) {
    // App-level check for a clean 409 before we attempt the INSERT.
    // The DB UNIQUE constraint on title is the source of truth — concurrent
    // requests that race past this check will still be rejected by Postgres.
    if (conversationRepository.existsByTitle(title)) {
      throw new DuplicateTitleException(title);
    }

    String summarized;
    try {
      summarized = summarizationService.summarize(rawContent);
    } catch (SummarizationException e) {
      log.error("Error summarizing conversation: {}", e.getMessage(), e);
      summarized = "[SUMMARIZATION_FAILED]";
    }

    // Save Conversation
    Conversation conversation = new Conversation();
    conversation.setTitle(title);
    conversation.setSource(source);
    conversation.setRawContent(rawContent);
    conversation.setSummary(summarized);
    conversationRepository.save(conversation);

    List<String> chunks = chunkingService.chunkText(rawContent);
    List<ConversationChunk> persistedChunks = new ArrayList<>();

    for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
      ConversationChunk chunk = new ConversationChunk();
      chunk.setConversationId(conversation.getId());
      chunk.setChunkIndex(chunkIndex);
      chunk.setContent(chunks.get(chunkIndex));
      conversationChunkRepository.save(chunk);
      persistedChunks.add(chunk);
    }

    try {
      embeddingService.embedChunks(persistedChunks);
    } catch (Exception e) {
      log.error("Embedding failed for conversation {}: {}", conversation.getId(), e.getMessage(), e);
    }
    return conversation;
  }

  public List<Conversation> searchConversations(String query) {
    return searchService.search(query);
  }

  // Deletes a conversation along with all its chunks. Single transaction so
  // either both deletes succeed or neither does. Chunks must go first because
  // the FK on conversation_chunks.conversation_id has no ON DELETE CASCADE.
  @Transactional
  public void deleteConversation(UUID id) {
    if (!conversationRepository.existsById(id)) {
      throw new ConversationNotFoundException(id);
    }
    conversationChunkRepository.deleteByConversationId(id);
    conversationRepository.deleteById(id);
  }
}
