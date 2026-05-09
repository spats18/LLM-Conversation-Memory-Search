package com.llmmemory.conversation.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.llmmemory.conversation.domain.ConversationRequest;
import com.llmmemory.conversation.domain.ConversationResponse;
import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.service.ConversationService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1")
@RestController
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            @RequestBody @Valid ConversationRequest request) {
        Conversation conversation = conversationService.createConversation(
                request.title(), request.source(), request.rawContent());

        ConversationResponse response = new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getSummary(),
                conversation.getCreatedAt());

        // Build the URI for the newly created conversation resource
        // Alt - String uri = String.format("/api/v1/conversations/%s",
        // conversation.id());
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(conversation.getId()).toUri();

        return ResponseEntity.created(uri).body(response);
    }

}
