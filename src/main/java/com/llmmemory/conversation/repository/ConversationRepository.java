package com.llmmemory.conversation.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.llmmemory.conversation.domain.entity.Conversation;

// Handles all database operations for the conversations table
// Extends JpaRepository for free CRUD methods — save(), findAll(), findById(), deleteById()
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    // Derived query — Spring Data generates SELECT count(*) > 0 ... WHERE title = ?
    boolean existsByTitle(String title);

    // Native query because @@ and plainto_tsquery are Postgres-specific.
    // search_vector is a generated tsvector column maintained by Postgres.
    // countQuery is required because Spring Data cannot derive a count from native SQL.
    @Query(
        value = "SELECT * FROM conversations "
            + "WHERE search_vector @@ plainto_tsquery('english', :query)",
        countQuery = "SELECT count(*) FROM conversations "
            + "WHERE search_vector @@ plainto_tsquery('english', :query)",
        nativeQuery = true)
    Page<Conversation> searchByKeyword(@Param("query") String query, Pageable pageable);
}
