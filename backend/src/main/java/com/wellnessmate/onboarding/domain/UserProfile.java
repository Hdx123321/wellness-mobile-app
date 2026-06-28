package com.wellnessmate.onboarding.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Private onboarding profile owned by one client account. @author TODO(team member) */
@Entity
@Table(name = "user_profiles")
public class UserProfile {
  @Id
  private Long userId;

  @Column(nullable = false)
  private LocalDate dateOfBirth;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal heightCm;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal currentWeightKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SexOption sex;

  @Enumerated(EnumType.STRING)
  @Column(length = 50)
  private EthnicityOption ethnicity;

  @Column(precision = 5, scale = 2)
  private BigDecimal targetWeightKg;

  private LocalDate goalStartedAt;
  private Integer goalDurationWeeks;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private DailyRoutine dailyRoutine;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ActivityLevel activityLevel;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "user_exercise_preferences", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "preference", nullable = false, length = 40)
  @Enumerated(EnumType.STRING)
  private Set<ExercisePreference> exercisePreferences = new HashSet<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "user_core_needs", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "core_need", nullable = false, length = 40)
  @Enumerated(EnumType.STRING)
  private Set<CoreNeed> coreNeeds = new HashSet<>();

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected UserProfile() {
  }

  public UserProfile(Long userId) {
    this.userId = userId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void update(LocalDate dateOfBirth, BigDecimal heightCm, BigDecimal currentWeightKg,
                     SexOption sex, EthnicityOption ethnicity, BigDecimal targetWeightKg,
                     Integer goalDurationWeeks, DailyRoutine dailyRoutine, ActivityLevel activityLevel,
                     Set<ExercisePreference> exercisePreferences, Set<CoreNeed> coreNeeds) {
    this.dateOfBirth = dateOfBirth;
    this.heightCm = heightCm;
    this.currentWeightKg = currentWeightKg;
    this.sex = sex;
    this.ethnicity = ethnicity;
    this.targetWeightKg = targetWeightKg;
    this.goalStartedAt = targetWeightKg == null ? null : LocalDate.now();
    this.goalDurationWeeks = targetWeightKg == null ? null : goalDurationWeeks;
    this.dailyRoutine = dailyRoutine;
    this.activityLevel = activityLevel;
    this.exercisePreferences.clear();
    this.exercisePreferences.addAll(exercisePreferences);
    this.coreNeeds.clear();
    this.coreNeeds.addAll(coreNeeds);
    this.updatedAt = Instant.now();
  }

  public Long getUserId() { return userId; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public BigDecimal getHeightCm() { return heightCm; }
  public BigDecimal getCurrentWeightKg() { return currentWeightKg; }
  public SexOption getSex() { return sex; }
  public EthnicityOption getEthnicity() { return ethnicity; }
  public BigDecimal getTargetWeightKg() { return targetWeightKg; }
  public LocalDate getGoalStartedAt() { return goalStartedAt; }
  public Integer getGoalDurationWeeks() { return goalDurationWeeks; }
  public DailyRoutine getDailyRoutine() { return dailyRoutine; }
  public ActivityLevel getActivityLevel() { return activityLevel; }
  public Set<ExercisePreference> getExercisePreferences() { return Set.copyOf(exercisePreferences); }
  public Set<CoreNeed> getCoreNeeds() { return Set.copyOf(coreNeeds); }
}
