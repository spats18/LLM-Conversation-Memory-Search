package com.llmmemory.conversation.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;

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

  public Conversation createConversation(String title, String source, String rawContent) {
    // App-level check for a clean 409 before we attempt the INSERT.
    // The DB UNIQUE constraint on title is the source of truth — concurrent
    // requests that race past this check will still be rejected by Postgres.
    if (conversationRepository.existsByTitle(title)) {
      throw new DuplicateTitleException(title);
    }

    // get Summarization first, so the client gets faster feedback if the LLM call
    // fails — before we do any DB writes or chunking.
    String summarized;
    try {
      summarized = summarizationService.summarize(rawContent);
    } catch (SummarizationException e) {
      log.error("Error summarizing conversation: {}", e.getMessage(), e);
      summarized = "[SUMMARIZATION_FAILED]";
    }

    // We have separated the Db and APi calls to LLMs to ensure that we don't keep
    // Db connections open while waiting for LLM responses, which can be slow.
    // If we have concurrent load and we keep Db connections open during LLM calls,
    // we can run out of Db connections, which causes cascading failures.
    Pair<Conversation, List<ConversationChunk>> conversationAndChunks = createAndSaveConversationInDb(title, source,
        rawContent, summarized);

    Conversation conversation = conversationAndChunks.getFirst();
    List<ConversationChunk> persistedChunks = conversationAndChunks.getSecond();
    try {
      embeddingService.embedChunks(persistedChunks);
    } catch (Exception e) {
      log.error("Embedding failed for conversation {}: {}", conversation.getId(), e.getMessage(), e);
    }
    return conversation;
  }

  @Transactional
  public Pair<Conversation, List<ConversationChunk>> createAndSaveConversationInDb(String title, String source,
      String rawContent, String summarized) {

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
      persistedChunks.add(chunk);
    }
    // batching the saving of chunks to avoid N+1 insert problem and to get better
    // performance.
    conversationChunkRepository.saveAll(persistedChunks);
    return Pair.of(conversation, persistedChunks);
  }

  public Page<Conversation> listConversations(Pageable pageable) {
    return conversationRepository.findAll(pageable);
  }

  public Page<Conversation> searchConversationsByKeyword(String query, Pageable pageable) {
    return conversationRepository.searchByKeyword(query, pageable);
  }

  public List<Conversation> searchConversations(String query) {
    return searchService.search(query);
  }

  // Deletes a conversation, its chunks, and its embeddings.
  // Embedding store uses its own connection outside Spring's transaction, so
  // embeddings
  // are removed first — a rollback on the DB side leaves no orphans; an
  // embedding-store
  // failure surfaces before the DB rows are touched.
  @Transactional
  public void deleteConversation(UUID id) {
    if (!conversationRepository.existsById(id)) {
      throw new ConversationNotFoundException(id);
    }
    embeddingService.deleteEmbeddingsByConversationId(id);
    conversationChunkRepository.deleteByConversationId(id);
    conversationRepository.deleteById(id);
  }
}
