package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodCatalogItem;
import java.math.BigDecimal;
import java.util.List;

public record FoodDetailResponse(
    Long id,
    String name,
    String imageUrl,
    BigDecimal caloriesPer100g,
    BigDecimal proteinPer100g,
    BigDecimal carbohydratePer100g,
    BigDecimal fatPer100g,
    BigDecimal fiberPer100g,
    Long categoryId,
    List<ServingSizeResponse> servingSizes
) {
  public static FoodDetailResponse from(FoodCatalogItem food, List<ServingSizeResponse> servingSizes) {
    return new FoodDetailResponse(
        food.getId(), food.getName(), food.getImageUrl(),
        food.getCaloriesPer100g(), food.getProteinPer100g(),
        food.getCarbohydratePer100g(), food.getFatPer100g(),
        food.getFiberPer100g(), food.getCategoryId(), servingSizes);
  }
}
