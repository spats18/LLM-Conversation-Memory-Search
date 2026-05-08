package com.llmmemory.conversation.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "conversations")
@Data
public class Conversation {

  // UUID: database-agnostic unique identifier — safer than auto-increment integers
  // @Id: marks this as the primary key
  // @GeneratedValue: Postgres generates the UUID automatically, we never set it manually
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // VARCHAR by default — titles are short, no need for TEXT
  private String title;

  // Tracks where the conversation came from: "paste", "url", "file"
  // VARCHAR by default — values are short and known
  private String source;

  // TEXT: conversation content can be thousands of characters, VARCHAR(255) would truncate it
  @Column(columnDefinition = "TEXT")
  private String rawContent;

  // TEXT: summaries can also be long depending on the conversation
  @Column(columnDefinition = "TEXT")
  private String summary;

  // @CreationTimestamp: Hibernate sets this automatically when the row is inserted
  // LocalDateTime: standard Java date-time type, maps cleanly to Postgres TIMESTAMP
  @CreationTimestamp private LocalDateTime createdAt;

  // Stores the Postgres tsvector value for full-text keyword search
  // String for now — Postgres will populate this via a trigger or manual update
  @Column(columnDefinition = "tsvector")
  private String searchVector;
}
