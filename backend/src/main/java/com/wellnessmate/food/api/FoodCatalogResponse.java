package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodCatalogItem;
import java.math.BigDecimal;

public record FoodCatalogResponse(
    Long id,
    String name,
    BigDecimal caloriesPer100g,
    BigDecimal proteinPer100g,
    BigDecimal carbohydratePer100g,
    BigDecimal fatPer100g,
    BigDecimal fiberPer100g
) {
  public static FoodCatalogResponse from(FoodCatalogItem food) {
    return new FoodCatalogResponse(food.getId(), food.getName(), food.getCaloriesPer100g(),
        food.getProteinPer100g(), food.getCarbohydratePer100g(), food.getFatPer100g(),
        food.getFiberPer100g());
  }
}
