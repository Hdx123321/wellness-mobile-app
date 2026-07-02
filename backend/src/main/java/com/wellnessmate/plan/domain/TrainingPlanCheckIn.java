package com.wellnessmate.plan.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "training_plan_check_ins")
public class TrainingPlanCheckIn {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long planId;
  private Long userId;
  private LocalDate checkInDate;
  private Instant createdAt;
  protected TrainingPlanCheckIn() {}
  public TrainingPlanCheckIn(Long planId, Long userId, LocalDate date) {
    this.planId = planId; this.userId = userId; this.checkInDate = date; this.createdAt = Instant.now();
  }
}
