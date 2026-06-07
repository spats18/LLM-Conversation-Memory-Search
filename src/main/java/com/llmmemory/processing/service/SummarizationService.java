package com.llmmemory.processing.service;

import org.springframework.stereotype.Service;

import com.llmmemory.processing.exception.SummarizationException;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SummarizationService {

    private static final String SYSTEM_PROMPT = "You are a helpful assistant that summarizes conversations. "
            + "Read the following conversation and provide a concise summary of the key points discussed. "
            + "Focus on the main topics, decisions made, and any action items mentioned. "
            + "Avoid minor details or off-topic discussions.";

    private final ChatModel chatModel;

    SummarizationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String summarize(String rawContent) throws SummarizationException {
        try {
            ChatResponse response = chatModel.chat(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(rawContent));
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("LangChain4j summarization failed: {}", e.getMessage(), e);
            throw new SummarizationException("Summarization failed: " + e.getMessage());
        }
    }
}
