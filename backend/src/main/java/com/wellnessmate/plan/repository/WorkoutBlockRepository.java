package com.wellnessmate.plan.repository;

import com.wellnessmate.plan.domain.WorkoutBlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutBlockRepository extends JpaRepository<WorkoutBlock, Long> {
  List<WorkoutBlock> findByPlanIdOrderBySortOrderAsc(Long planId);
  void deleteByPlanId(Long planId);
}
