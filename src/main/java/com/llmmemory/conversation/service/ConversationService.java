package com.llmmemory.conversation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.domain.entity.ConversationChunk;
import com.llmmemory.conversation.repository.ConversationChunkRepository;
import com.llmmemory.conversation.repository.ConversationRepository;
import com.llmmemory.shared.exception.ConversationNotFoundException;
import com.llmmemory.shared.exception.DuplicateTitleException;
import com.llmmemory.summarization.exception.SummarizationException;
import com.llmmemory.summarization.service.SummarizationService;

import org.springframework.beans.factory.annotation.Value;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ConversationService {
  private final int chunkSize;
  private final SummarizationService summarizationService;
  private final ConversationRepository conversationRepository;
  private final ConversationChunkRepository conversationChunkRepository;

  public ConversationService(
      SummarizationService summarizationService,
      ConversationRepository conversationRepository,
      ConversationChunkRepository conversationChunkRepository,
      @Value("${app.chunking.size:500}") int chunkSize) {
    this.summarizationService = summarizationService;
    this.conversationRepository = conversationRepository;
    this.conversationChunkRepository = conversationChunkRepository;
    this.chunkSize = chunkSize;
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

    // Fixed-window chunking: split into consecutive non-overlapping slices of
    // chunkSize chars.
    // Phase 2 will replace this with smarter chunking (overlap, sentence
    // boundaries).
    int chunkCounts = (int) Math.ceil((double) rawContent.length() / chunkSize);

    for (int chunkIndex = 0; chunkIndex < chunkCounts; chunkIndex++) {
      int start = chunkIndex * chunkSize;
      int end = Math.min(start + chunkSize, rawContent.length());
      String chunkContent = rawContent.substring(start, end);

      ConversationChunk chunk = new ConversationChunk();
      chunk.setConversationId(conversation.getId());
      chunk.setChunkIndex(chunkIndex);
      chunk.setContent(chunkContent);
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
