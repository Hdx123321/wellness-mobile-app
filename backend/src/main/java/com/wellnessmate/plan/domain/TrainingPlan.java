package com.wellnessmate.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "training_plans")
public class TrainingPlan {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long coachId;
  @Column(nullable = false, length = 120) private String title;
  @Column(nullable = false, length = 120) private String goal;
  @Column(nullable = false, length = 30) private String difficulty;
  @Column(nullable = false) private int durationWeeks;
  @Column(nullable = false, length = 1000) private String summary;
  @Column(nullable = false, columnDefinition = "TEXT") private String weeklySchedule;
  @Column(length = 500) private String equipment;
  @Column(length = 1000) private String safetyNotes;
  @Column(length = 1000) private String videoUrl;
  @Column(nullable = false) private boolean published;
  @Column(nullable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;

  protected TrainingPlan() {}

  public TrainingPlan(Long coachId, String title, String goal, String difficulty, int durationWeeks,
                      String summary, String weeklySchedule, String equipment, String safetyNotes,
                      String videoUrl) {
    this.coachId = coachId;
    this.title = title.trim();
    this.goal = goal.trim();
    this.difficulty = difficulty.trim();
    this.durationWeeks = durationWeeks;
    this.summary = summary.trim();
    this.weeklySchedule = weeklySchedule.trim();
    this.equipment = normalize(equipment);
    this.safetyNotes = normalize(safetyNotes);
    this.videoUrl = normalize(videoUrl);
    this.published = true;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  public Long getId() { return id; }
  public Long getCoachId() { return coachId; }
  public String getTitle() { return title; }
  public String getGoal() { return goal; }
  public String getDifficulty() { return difficulty; }
  public int getDurationWeeks() { return durationWeeks; }
  public String getSummary() { return summary; }
  public String getWeeklySchedule() { return weeklySchedule; }
  public String getEquipment() { return equipment; }
  public String getSafetyNotes() { return safetyNotes; }
  public String getVideoUrl() { return videoUrl; }
  public boolean isPublished() { return published; }
  public Instant getCreatedAt() { return createdAt; }
}
