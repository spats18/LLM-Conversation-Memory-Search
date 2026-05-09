package com.llmmemory.conversation.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String title,
        String summary,
        LocalDateTime createdAt) {
}