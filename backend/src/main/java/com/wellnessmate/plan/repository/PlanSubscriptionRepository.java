package com.wellnessmate.plan.repository;

import com.wellnessmate.plan.domain.PlanSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanSubscriptionRepository extends JpaRepository<PlanSubscription, Long> {
  boolean existsByPlanIdAndUserId(Long planId, Long userId);
  List<PlanSubscription> findByPlanId(Long planId);
  Optional<PlanSubscription> findByPlanIdAndUserId(Long planId, Long userId);
  void deleteByPlanIdAndUserId(Long planId, Long userId);
  List<PlanSubscription> findByUserId(Long userId);

  @Query("SELECT DISTINCT s.userId FROM PlanSubscription s WHERE s.planId IN :planIds")
  List<Long> findDistinctUserIdByPlanIdIn(List<Long> planIds);
}
