package com.llmmemory.conversation.repository;

import com.llmmemory.conversation.domain.Conversation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Handles all database operations for the conversations table
// Extends JpaRepository for free CRUD methods — save(), findAll(), findById(), deleteById()
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  // Custom query needed because @@ and plainto_tsquery are Postgres-specific syntax
  // nativeQuery = true: sends SQL directly to Postgres instead of translating it
  // plainto_tsquery: converts the search term into a format Postgres matches against search_vector
  // :query is the keyword passed in at runtime via @Param
  @Query(
      value =
          "SELECT * FROM conversations WHERE search_vector @@ plainto_tsquery('english', :query)",
      nativeQuery = true)
  List<Conversation> searchByKeyword(@Param("query") String query);
}
