package com.wellnessmate.plan.service;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.domain.UserRole;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.plan.api.SubscriberResponse;
import com.wellnessmate.plan.api.TrainingPlanRequest;
import com.wellnessmate.plan.api.TrainingPlanResponse;
import com.wellnessmate.plan.api.WorkoutBlockRequest;
import com.wellnessmate.plan.api.WorkoutBlockResponse;
import com.wellnessmate.plan.domain.PlanSubscription;
import com.wellnessmate.plan.domain.TrainingPlan;
import com.wellnessmate.plan.domain.TrainingPlanCheckIn;
import com.wellnessmate.plan.domain.WorkoutBlock;
import com.wellnessmate.plan.repository.PlanSubscriptionRepository;
import com.wellnessmate.plan.repository.TrainingPlanCheckInRepository;
import com.wellnessmate.plan.repository.TrainingPlanRepository;
import com.wellnessmate.plan.repository.WorkoutBlockRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingPlanService {
  private final TrainingPlanRepository plans;
  private final TrainingPlanCheckInRepository checkIns;
  private final WorkoutBlockRepository blocks;
  private final PlanSubscriptionRepository subscriptions;
  private final UserAccountRepository users;

  public TrainingPlanService(TrainingPlanRepository plans, TrainingPlanCheckInRepository checkIns,
                             WorkoutBlockRepository blocks, PlanSubscriptionRepository subscriptions,
                             UserAccountRepository users) {
    this.plans = plans;
    this.checkIns = checkIns;
    this.blocks = blocks;
    this.subscriptions = subscriptions;
    this.users = users;
  }

  // ── Read ──

  @Transactional(readOnly = true)
  public List<TrainingPlanResponse> list(Long userId) {
    requireUser(userId);
    return plans.findByPublishedTrueOrderByCreatedAtDesc().stream()
        .map(plan -> response(plan, userId)).toList();
  }

  @Transactional(readOnly = true)
  public TrainingPlanResponse get(Long userId, Long id) {
    return response(requirePlan(id), userId);
  }

  // ── Create / Update / Delete ──

  @Transactional
  public TrainingPlanResponse create(Long userId, TrainingPlanRequest request) {
    UserAccount coach = requireUser(userId);
    if (coach.getRole() != UserRole.COACH)
      throw new ApiException(HttpStatus.FORBIDDEN, "COACH_REQUIRED",
          "Only coaches can publish training plans");
    TrainingPlan saved = plans.save(new TrainingPlan(userId, request.title(), request.goal(),
        request.difficulty(), request.durationWeeks(), request.summary(), request.weeklySchedule(),
        request.equipment(), request.safetyNotes(), request.videoUrl()));
    saveBlocks(saved.getId(), request.blocks());
    return response(saved, userId);
  }

  @Transactional
  public TrainingPlanResponse update(Long userId, Long planId, TrainingPlanRequest request) {
    TrainingPlan plan = requireOwnPlan(userId, planId);
    plan.setTitle(request.title());
    plan.setGoal(request.goal());
    plan.setDifficulty(request.difficulty());
    plan.setDurationWeeks(request.durationWeeks());
    plan.setSummary(request.summary());
    plan.setWeeklySchedule(request.weeklySchedule());
    plan.setEquipment(request.equipment());
    plan.setSafetyNotes(request.safetyNotes());
    plan.setVideoUrl(request.videoUrl());
    plans.save(plan);
    blocks.deleteByPlanId(planId);
    saveBlocks(planId, request.blocks());
    return response(plan, userId);
  }

  @Transactional
  public void delete(Long userId, Long planId) {
    requireOwnPlan(userId, planId);
    plans.deleteById(planId);
  }

  // ── Check-in (subscription-gated) ──

  @Transactional
  public TrainingPlanResponse checkIn(Long userId, Long id) {
    TrainingPlan plan = requirePlan(id);
    if (!subscriptions.existsByPlanIdAndUserId(id, userId))
      throw new ApiException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_REQUIRED",
          "Subscribe to this plan before you can check in");
    LocalDate today = LocalDate.now();
    if (!checkIns.existsByPlanIdAndUserIdAndCheckInDate(id, userId, today)) {
      checkIns.save(new TrainingPlanCheckIn(id, userId, today));
    }
    return response(plan, userId);
  }

  // ── Subscriptions ──

  @Transactional
  public TrainingPlanResponse subscribe(Long userId, Long planId) {
    requirePlan(planId);
    if (subscriptions.existsByPlanIdAndUserId(planId, userId))
      throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
          "You are already subscribed to this plan");
    subscriptions.save(new PlanSubscription(planId, userId));
    return get(userId, planId);
  }

  @Transactional
  public void unsubscribe(Long userId, Long planId) {
    requirePlan(planId);
    subscriptions.deleteByPlanIdAndUserId(planId, userId);
  }

  @Transactional(readOnly = true)
  public List<TrainingPlanResponse> subscribed(Long userId) {
    requireUser(userId);
    return subscriptions.findByUserId(userId).stream()
        .map(sub -> plans.findById(sub.getPlanId()).filter(TrainingPlan::isPublished))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(plan -> response(plan, userId))
        .toList();
  }

  // ── Subscribers ──

  @Transactional(readOnly = true)
  public List<SubscriberResponse> subscribers(Long coachId, Long planId) {
    TrainingPlan plan = requirePlan(planId);
    if (!plan.getCoachId().equals(coachId))
      throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_PLAN",
          "Only the plan's coach can view subscribers");
    List<PlanSubscription> subs = subscriptions.findByPlanId(planId);
    return subs.stream().map(sub -> {
      UserAccount u = requireUser(sub.getUserId());
      return new SubscriberResponse(u.getId(), u.getUsername(), u.getDisplayName(),
          sub.getSubscribedAt());
    }).toList();
  }

  // ── Coach's clients (all subscribers across all plans) ──

  @Transactional(readOnly = true)
  public List<SubscriberResponse> coachClients(Long coachId) {
    requireUser(coachId);
    List<TrainingPlan> coachPlans = plans.findByCoachIdAndPublishedTrueOrderByCreatedAtDesc(coachId);
    List<Long> planIds = coachPlans.stream().map(TrainingPlan::getId).toList();
    if (planIds.isEmpty()) return List.of();
    return subscriptions.findDistinctUserIdByPlanIdIn(planIds).stream()
        .map(userId -> {
          UserAccount u = requireUser(userId);
          return new SubscriberResponse(u.getId(), u.getUsername(), u.getDisplayName(), null);
        }).toList();
  }

  // ── Helpers ──

  private void saveBlocks(Long planId, List<WorkoutBlockRequest> blockRequests) {
    for (int i = 0; i < blockRequests.size(); i++) {
      WorkoutBlockRequest r = blockRequests.get(i);
      blocks.save(new WorkoutBlock(planId, i, r.title(), r.content(), r.imageUrl(), r.videoUrl()));
    }
  }

  private TrainingPlanResponse response(TrainingPlan plan, Long userId) {
    UserAccount coach = requireUser(plan.getCoachId());
    String name = coach.getDisplayName() == null ? coach.getUsername() : coach.getDisplayName();
    List<WorkoutBlockResponse> blockList = blocks.findByPlanIdOrderBySortOrderAsc(plan.getId())
        .stream().map(b -> new WorkoutBlockResponse(b.getId(), b.getSortOrder(), b.getTitle(),
            b.getContent(), b.getImageUrl(), b.getVideoUrl())).toList();
    boolean isSubscribed = subscriptions.existsByPlanIdAndUserId(plan.getId(), userId);
    return new TrainingPlanResponse(plan.getId(), plan.getCoachId(), name, plan.getTitle(),
        plan.getGoal(), plan.getDifficulty(), plan.getDurationWeeks(), plan.getSummary(),
        plan.getWeeklySchedule(), plan.getEquipment(), plan.getSafetyNotes(), plan.getVideoUrl(),
        blockList,
        checkIns.countByPlanIdAndUserId(plan.getId(), userId),
        checkIns.existsByPlanIdAndUserIdAndCheckInDate(plan.getId(), userId, LocalDate.now()),
        isSubscribed,
        plan.getCreatedAt());
  }

  private TrainingPlan requirePlan(Long id) {
    return plans.findById(id).filter(TrainingPlan::isPublished)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "TRAINING_PLAN_NOT_FOUND", "Training plan not found"));
  }

  private TrainingPlan requireOwnPlan(Long userId, Long planId) {
    TrainingPlan plan = plans.findById(planId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "TRAINING_PLAN_NOT_FOUND", "Training plan not found"));
    if (!plan.getCoachId().equals(userId))
      throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_PLAN",
          "You can only modify your own plans");
    return plan;
  }

  private UserAccount requireUser(Long id) {
    return users.findById(id).orElseThrow(() ->
        new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found"));
  }
}
