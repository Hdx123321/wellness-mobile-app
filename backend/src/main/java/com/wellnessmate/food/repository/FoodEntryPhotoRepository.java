package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodEntryPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodEntryPhotoRepository extends JpaRepository<FoodEntryPhoto, Long> {
  boolean existsByFoodEntryId(Long foodEntryId);
  List<FoodEntryPhoto> findByFoodEntryIdIn(Collection<Long> foodEntryIds);
  Optional<FoodEntryPhoto> findByFoodEntryId(Long foodEntryId);
  void deleteByFoodEntryId(Long foodEntryId);
}
