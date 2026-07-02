package com.wellnessmate.plan.service;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.domain.UserRole;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.plan.api.TrainingPlanRequest;
import com.wellnessmate.plan.api.TrainingPlanResponse;
import com.wellnessmate.plan.domain.TrainingPlan;
import com.wellnessmate.plan.domain.TrainingPlanCheckIn;
import com.wellnessmate.plan.repository.TrainingPlanCheckInRepository;
import com.wellnessmate.plan.repository.TrainingPlanRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingPlanService {
  private final TrainingPlanRepository plans;
  private final TrainingPlanCheckInRepository checkIns;
  private final UserAccountRepository users;
  public TrainingPlanService(TrainingPlanRepository plans, TrainingPlanCheckInRepository checkIns,
                             UserAccountRepository users) {
    this.plans = plans; this.checkIns = checkIns; this.users = users;
  }

  @Transactional(readOnly = true)
  public List<TrainingPlanResponse> list(Long userId) {
    requireUser(userId);
    return plans.findByPublishedTrueOrderByCreatedAtDesc().stream().map(plan -> response(plan, userId)).toList();
  }

  @Transactional(readOnly = true)
  public TrainingPlanResponse get(Long userId, Long id) { return response(requirePlan(id), userId); }

  @Transactional
  public TrainingPlanResponse create(Long userId, TrainingPlanRequest request) {
    UserAccount coach = requireUser(userId);
    if (coach.getRole() != UserRole.COACH) throw new ApiException(HttpStatus.FORBIDDEN,
        "COACH_REQUIRED", "Only coaches can publish training plans");
    TrainingPlan saved = plans.save(new TrainingPlan(userId, request.title(), request.goal(),
        request.difficulty(), request.durationWeeks(), request.summary(), request.weeklySchedule(),
        request.equipment(), request.safetyNotes(), request.videoUrl()));
    return response(saved, userId);
  }

  @Transactional
  public TrainingPlanResponse checkIn(Long userId, Long id) {
    TrainingPlan plan = requirePlan(id);
    LocalDate today = LocalDate.now();
    if (!checkIns.existsByPlanIdAndUserIdAndCheckInDate(id, userId, today)) {
      checkIns.save(new TrainingPlanCheckIn(id, userId, today));
    }
    return response(plan, userId);
  }

  private TrainingPlan requirePlan(Long id) { return plans.findById(id).filter(TrainingPlan::isPublished)
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRAINING_PLAN_NOT_FOUND", "Training plan not found")); }
  private UserAccount requireUser(Long id) { return users.findById(id).orElseThrow(() ->
      new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found")); }
  private TrainingPlanResponse response(TrainingPlan plan, Long userId) {
    UserAccount coach = requireUser(plan.getCoachId());
    String name = coach.getDisplayName() == null ? coach.getUsername() : coach.getDisplayName();
    return new TrainingPlanResponse(plan.getId(), plan.getCoachId(), name, plan.getTitle(), plan.getGoal(),
        plan.getDifficulty(), plan.getDurationWeeks(), plan.getSummary(), plan.getWeeklySchedule(),
        plan.getEquipment(), plan.getSafetyNotes(), plan.getVideoUrl(), checkIns.countByPlanIdAndUserId(plan.getId(), userId),
        checkIns.existsByPlanIdAndUserIdAndCheckInDate(plan.getId(), userId, LocalDate.now()), plan.getCreatedAt());
  }
}
