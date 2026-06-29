package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodEntryItem;
import java.math.BigDecimal;

public record FoodEntryItemResponse(
    Long id,
    Long catalogItemId,
    String name,
    BigDecimal grams,
    FoodNutrients nutrients
) {
  public static FoodEntryItemResponse from(FoodEntryItem item) {
    return new FoodEntryItemResponse(item.getId(), item.getCatalogItemId(), item.getFoodName(),
        item.getGrams(), new FoodNutrients(item.getCalories(), item.getProteinGrams(),
        item.getCarbohydrateGrams(), item.getFatGrams(), item.getFiberGrams()));
  }
}
