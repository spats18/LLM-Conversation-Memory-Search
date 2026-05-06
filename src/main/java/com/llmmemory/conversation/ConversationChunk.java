package com.llmmemory.conversation;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_chunks")
@Data
public class ConversationChunk {

    // Primary key for this chunk row
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Foreign key — links this chunk back to its parent conversation
    // NOT a primary key, just a reference
    @Column(name = "conversation_id")
    private UUID conversationId;

    // Tracks the order of this chunk within the conversation (0, 1, 2, ...)
    private int chunkIndex;

    // The actual text content of this chunk — TEXT because it can be large
    @Column(columnDefinition = "TEXT")
    private String content;

    // Set automatically by Hibernate when the chunk is saved
    @CreationTimestamp
    private LocalDateTime createdAt;
}
