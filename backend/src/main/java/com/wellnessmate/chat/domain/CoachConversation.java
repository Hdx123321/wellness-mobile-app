package com.wellnessmate.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "coach_conversations")
public class CoachConversation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private Long clientId;
  @Column(nullable = false)
  private Long coachId;
  @Column(length = 120)
  private String subject;
  @Column(nullable = false)
  private Instant createdAt;
  @Column(nullable = false)
  private Instant updatedAt;

  protected CoachConversation() {}

  public CoachConversation(Long clientId, Long coachId) {
    this.clientId = clientId;
    this.coachId = coachId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public CoachConversation(Long clientId, Long coachId, String subject) {
    this.clientId = clientId;
    this.coachId = coachId;
    this.subject = (subject == null || subject.isBlank()) ? null : subject.trim();
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void touch(Instant time) { updatedAt = time; }
  public Long getId() { return id; }
  public Long getClientId() { return clientId; }
  public Long getCoachId() { return coachId; }
  public String getSubject() { return subject; }
  public Instant getUpdatedAt() { return updatedAt; }
}
