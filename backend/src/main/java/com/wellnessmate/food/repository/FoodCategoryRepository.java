package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCategoryRepository extends JpaRepository<FoodCategory, Long> {
  List<FoodCategory> findAllByOrderBySortOrderAsc();
}
