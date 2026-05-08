package com.llmmemory.conversation.service;

import com.llmmemory.conversation.domain.Conversation;
import com.llmmemory.conversation.domain.ConversationChunk;
import com.llmmemory.conversation.repository.ConversationChunkRepository;
import com.llmmemory.conversation.repository.ConversationRepository;
import com.llmmemory.summarization.exception.SummarizationException;
import com.llmmemory.summarization.service.SummarizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

  public Conversation createConversation(String title, String rawContent, String source) {
    String summarized;
    try {
      summarized = summarizationService.summarize(rawContent);
    } catch (SummarizationException e) {
      // Handle the exception, e.g., log it and return an error message
      log.error("Error summarizing conversation: {}", e.getMessage(), e);
      summarized = "Summary unavailable due to an error while summarizing: " + e.getMessage();
    }

    // Save Conversation
    Conversation conversation = new Conversation();
    conversation.setTitle(title);
    conversation.setSource(source);
    conversation.setRawContent(rawContent);
    conversation.setSummary(summarized);
    conversationRepository.save(conversation);

    // Chunk the raw content — split by \n\n or every 500 characters
    // save each chunk as a ConversationChunk
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
}
