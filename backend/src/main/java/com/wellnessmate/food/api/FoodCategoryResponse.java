package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodCategory;

public record FoodCategoryResponse(Long id, String name, String nameCn) {
  public static FoodCategoryResponse from(FoodCategory category) {
    return new FoodCategoryResponse(category.getId(), category.getName(), category.getNameCn());
  }
}
