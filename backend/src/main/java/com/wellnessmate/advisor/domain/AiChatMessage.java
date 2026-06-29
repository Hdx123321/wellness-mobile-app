package com.wellnessmate.advisor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class AiChatMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private Long sessionId;
  @Column(nullable = false, length = 20)
  private String role;
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;
  @Column(nullable = false)
  private Instant createdAt;

  protected AiChatMessage() {}

  public AiChatMessage(Long sessionId, String role, String content, Instant createdAt) {
    this.sessionId = sessionId;
    this.role = role;
    this.content = content;
    this.createdAt = createdAt;
  }

  public Long getId() { return id; }
  public Long getSessionId() { return sessionId; }
  public String getRole() { return role; }
  public String getContent() { return content; }
  public Instant getCreatedAt() { return createdAt; }
}
