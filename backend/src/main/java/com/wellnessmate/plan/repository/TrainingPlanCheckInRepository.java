package com.wellnessmate.plan.repository;

import com.wellnessmate.plan.domain.TrainingPlanCheckIn;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPlanCheckInRepository extends JpaRepository<TrainingPlanCheckIn, Long> {
  boolean existsByPlanIdAndUserIdAndCheckInDate(Long planId, Long userId, LocalDate date);
  long countByPlanIdAndUserId(Long planId, Long userId);
}
