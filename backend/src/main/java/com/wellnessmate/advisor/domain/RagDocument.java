package com.wellnessmate.advisor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rag_documents")
public class RagDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = true)
  private Long sessionId;

  @Column(nullable = false, length = 30)
  private String docType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false, columnDefinition = "JSON")
  private String embedding;

  @Column(nullable = false, columnDefinition = "JSON")
  private String metadata;

  @Column(nullable = false)
  private Instant createdAt;

  protected RagDocument() {}

  public RagDocument(Long userId, Long sessionId, String docType, String title,
                     String content, String embedding, String metadata) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.docType = docType;
    this.title = title;
    this.content = content;
    this.embedding = embedding;
    this.metadata = metadata;
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public Long getSessionId() { return sessionId; }
  public String getDocType() { return docType; }
  public String getTitle() { return title; }
  public String getContent() { return content; }
  public String getEmbedding() { return embedding; }
  public String getMetadata() { return metadata; }
  public Instant getCreatedAt() { return createdAt; }
}
