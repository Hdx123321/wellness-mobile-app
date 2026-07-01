package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodServingSize;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodServingSizeRepository extends JpaRepository<FoodServingSize, Long> {
  List<FoodServingSize> findByCatalogItemIdOrderBySortOrderAsc(Long catalogItemId);
}
