package com.llmmemory.conversation.service;

import java.util.*;

import org.springframework.stereotype.Service;

import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.domain.entity.ConversationChunk;
import com.llmmemory.conversation.repository.ConversationChunkRepository;
import com.llmmemory.conversation.repository.ConversationRepository;
import com.llmmemory.pipeline.ChunkingService;
import com.llmmemory.shared.exception.ConversationNotFoundException;
import com.llmmemory.shared.exception.DuplicateTitleException;
import com.llmmemory.summarization.exception.SummarizationException;
import com.llmmemory.summarization.service.SummarizationService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ConversationService {
  private final ChunkingService chunkingService;
  private final SummarizationService summarizationService;
  private final ConversationRepository conversationRepository;
  private final ConversationChunkRepository conversationChunkRepository;

  public ConversationService(
      ChunkingService chunkingService,
      SummarizationService summarizationService,
      ConversationRepository conversationRepository,
      ConversationChunkRepository conversationChunkRepository) {
    this.chunkingService = chunkingService;
    this.summarizationService = summarizationService;
    this.conversationRepository = conversationRepository;
    this.conversationChunkRepository = conversationChunkRepository;
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

    for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
      ConversationChunk chunk = new ConversationChunk();
      chunk.setConversationId(conversation.getId());
      chunk.setChunkIndex(chunkIndex);
      chunk.setContent(chunks.get(chunkIndex));
      conversationChunkRepository.save(chunk);
    }
    return conversation;
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
