package com.llmmemory.conversation.repository;

import com.llmmemory.conversation.domain.ConversationChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA implements this interface at runtime — no SQL needed
// handles all database operations for chunks
// JpaRepository<ConversationChunk, UUID>:
//   - ConversationChunk: the entity this repository manages
//   - UUID: the type of its primary key
// Free methods available: save(), findById(), findAll(), deleteById()
public interface ConversationChunkRepository extends JpaRepository<ConversationChunk, UUID> {
  List<ConversationChunk> findByConversationIdOrderByChunkIndex(UUID conversationId);
}
