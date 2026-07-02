package com.wellnessmate.plan.repository;

import com.wellnessmate.plan.domain.TrainingPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
  List<TrainingPlan> findByPublishedTrueOrderByCreatedAtDesc();
}
