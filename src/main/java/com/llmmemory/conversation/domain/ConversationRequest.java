package com.llmmemory.conversation.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConversationRequest(
        @NotBlank String title,
        @NotBlank @Pattern(regexp = "paste|url|file", message = "source must be one of: paste, url, file") String source,
        @NotBlank String rawContent) {
}