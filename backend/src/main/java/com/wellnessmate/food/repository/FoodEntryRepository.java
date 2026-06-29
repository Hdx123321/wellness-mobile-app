package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, Long> {
  List<FoodEntry> findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
      Long userId, Instant from, Instant to);

  Optional<FoodEntry> findByIdAndUserId(Long id, Long userId);
}
