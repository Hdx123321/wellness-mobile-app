package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodEntryItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodEntryItemRepository extends JpaRepository<FoodEntryItem, Long> {
  List<FoodEntryItem> findByFoodEntryIdInOrderById(Collection<Long> foodEntryIds);
}
