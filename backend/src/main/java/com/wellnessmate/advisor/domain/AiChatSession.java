package com.wellnessmate.advisor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_sessions")
public class AiChatSession {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private Long userId;
  @Column(length = 150)
  private String title;
  @Column(nullable = false)
  private Instant createdAt;
  @Column(nullable = false)
  private Instant updatedAt;

  protected AiChatSession() {}

  public AiChatSession(Long userId) {
    this.userId = userId;
    this.title = "AI wellness advisor";
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void touch(Instant time) { updatedAt = time; }
  public Long getId() { return id; }
  public Long getUserId() { return userId; }
}
