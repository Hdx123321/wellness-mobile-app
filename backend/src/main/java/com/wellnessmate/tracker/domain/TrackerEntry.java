package com.wellnessmate.tracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** One timestamped value in a built-in user tracker. @author TODO(team member) */
@Entity
@Table(name = "tracker_entries")
public class TrackerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TrackerType trackerType;

  @Column(nullable = false)
  private Instant recordedAt;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(length = 255)
  private String detail;

  @Column(length = 1000)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TrackerSource source;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected TrackerEntry() {
  }

  public TrackerEntry(Long userId, TrackerType trackerType, Instant recordedAt,
                      BigDecimal amount, String detail, String notes) {
    this.userId = userId;
    this.source = TrackerSource.MANUAL;
    this.createdAt = Instant.now();
    update(trackerType, recordedAt, amount, detail, notes);
  }

  public void update(TrackerType trackerType, Instant recordedAt, BigDecimal amount,
                     String detail, String notes) {
    this.trackerType = trackerType;
    this.recordedAt = recordedAt;
    this.amount = amount;
    this.detail = detail;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public TrackerType getTrackerType() { return trackerType; }
  public Instant getRecordedAt() { return recordedAt; }
  public BigDecimal getAmount() { return amount; }
  public String getDetail() { return detail; }
  public String getNotes() { return notes; }
  public TrackerSource getSource() { return source; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
