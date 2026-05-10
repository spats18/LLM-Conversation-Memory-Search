package com.llmmemory.conversation.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.llmmemory.conversation.domain.ConversationRequest;
import com.llmmemory.conversation.domain.ConversationResponse;
import com.llmmemory.conversation.domain.PagedResponse;
import com.llmmemory.conversation.domain.entity.Conversation;
import com.llmmemory.conversation.repository.ConversationRepository;
import com.llmmemory.conversation.service.ConversationService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1")
@RestController
public class ConversationController {
        private final ConversationService conversationService;
        private final ConversationRepository conversationRepository;

        public ConversationController(
                        ConversationService conversationService,
                        ConversationRepository conversationRepository) {
                this.conversationService = conversationService;
                this.conversationRepository = conversationRepository;
        }

        @PostMapping("/conversations")
        public ResponseEntity<ConversationResponse> createConversation(
                        @RequestBody @Valid ConversationRequest request) {
                Conversation conversation = conversationService.createConversation(
                                request.title(), request.source(), request.rawContent());

                ConversationResponse response = ConversationResponse.from(conversation);

                // Build the URI for the newly created conversation resource
                URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{id}").buildAndExpand(conversation.getId()).toUri();

                return ResponseEntity.created(uri).body(response);
        }

        @GetMapping("/conversations")
        public PagedResponse<ConversationResponse> listConversations(Pageable pageable) {
                Page<ConversationResponse> page = conversationRepository.findAll(pageable)
                                .map(ConversationResponse::from);

                return new PagedResponse<>(
                                page.getContent(),
                                page.getNumber(),
                                page.getSize(),
                                page.getTotalElements(),
                                page.getTotalPages());
        }

        @GetMapping("/conversations/search")
        public PagedResponse<ConversationResponse> searchConversations(
                        @RequestParam String query, Pageable pageable) {
                Page<ConversationResponse> page = conversationRepository.searchByKeyword(query, pageable)
                                .map(ConversationResponse::from);

                return new PagedResponse<>(
                                page.getContent(),
                                page.getNumber(),
                                page.getSize(),
                                page.getTotalElements(),
                                page.getTotalPages());
        }

        @DeleteMapping("/conversations/{id}")
        public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
                conversationService.deleteConversation(id);
                return ResponseEntity.noContent().build();
        }
}
