package com.llmmemory.conversation.domain;

import jakarta.validation.constraints.NotBlank;

public record ConversationRequest(
        @NotBlank String title,
        String source,
        @NotBlank String rawContent) {
}