package com.wellnessmate.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "coach_messages")
public class CoachMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private Long conversationId;
  @Column(nullable = false)
  private Long senderId;
  @Column(nullable = false, length = 2000)
  private String content;
  @Column(nullable = false)
  private Instant createdAt;

  protected CoachMessage() {}

  public CoachMessage(Long conversationId, Long senderId, String content, Instant createdAt) {
    this.conversationId = conversationId;
    this.senderId = senderId;
    this.content = content;
    this.createdAt = createdAt;
  }

  public Long getId() { return id; }
  public Long getConversationId() { return conversationId; }
  public Long getSenderId() { return senderId; }
  public String getContent() { return content; }
  public Instant getCreatedAt() { return createdAt; }
}
