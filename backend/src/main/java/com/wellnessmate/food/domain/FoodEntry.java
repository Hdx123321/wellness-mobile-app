package com.wellnessmate.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One confirmed meal linked to its aggregate FOOD tracker entry. */
@Entity
@Table(name = "food_entries")
public class FoodEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false, unique = true)
  private Long trackerEntryId;

  @Column(nullable = false)
  private Instant recordedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FoodEntrySource source;

  @Column(length = 1000)
  private String notes;

  @Column(nullable = false)
  private Instant createdAt;

  protected FoodEntry() {
  }

  public FoodEntry(Long userId, Long trackerEntryId, Instant recordedAt,
                   FoodEntrySource source, String notes) {
    this.userId = userId;
    this.trackerEntryId = trackerEntryId;
    this.recordedAt = recordedAt;
    this.source = source;
    this.notes = notes;
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public Long getTrackerEntryId() { return trackerEntryId; }
  public Instant getRecordedAt() { return recordedAt; }
  public FoodEntrySource getSource() { return source; }
  public String getNotes() { return notes; }
}
