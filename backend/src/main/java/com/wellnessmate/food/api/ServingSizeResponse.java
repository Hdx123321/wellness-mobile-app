package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodServingSize;
import java.math.BigDecimal;

public record ServingSizeResponse(Long id, String label, String labelCn, BigDecimal grams, boolean isDefault) {
  public static ServingSizeResponse from(FoodServingSize size) {
    return new ServingSizeResponse(size.getId(), size.getLabel(), size.getLabelCn(),
        size.getGrams(), size.isDefault());
  }
}
