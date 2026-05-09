package com.llmmemory.conversation.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.llmmemory.conversation.domain.entity.Conversation;

public record ConversationResponse(
        UUID id,
        String title,
        String summary,
        LocalDateTime createdAt) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getSummary(),
                conversation.getCreatedAt());
    }
}