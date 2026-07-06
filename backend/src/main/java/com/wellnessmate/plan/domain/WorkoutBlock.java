package com.wellnessmate.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workout_blocks")
public class WorkoutBlock {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long planId;
  @Column(nullable = false) private int sortOrder;
  @Column(nullable = false, length = 200) private String title;
  @Column(columnDefinition = "TEXT") private String content;
  @Column(length = 500) private String imageUrl;
  @Column(length = 500) private String videoUrl;
  @Column(nullable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;

  protected WorkoutBlock() {}

  public WorkoutBlock(Long planId, int sortOrder, String title, String content,
                      String imageUrl, String videoUrl) {
    this.planId = planId;
    this.sortOrder = sortOrder;
    this.title = title.trim();
    this.content = normalize(content);
    this.imageUrl = normalize(imageUrl);
    this.videoUrl = normalize(videoUrl);
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  private String normalize(String v) { return v == null || v.isBlank() ? null : v.trim(); }

  public Long getId() { return id; }
  public Long getPlanId() { return planId; }
  public int getSortOrder() { return sortOrder; }
  public String getTitle() { return title; }
  public String getContent() { return content; }
  public String getImageUrl() { return imageUrl; }
  public String getVideoUrl() { return videoUrl; }
  public Instant getCreatedAt() { return createdAt; }
}
