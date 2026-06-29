package com.wellnessmate.food.api;

import java.math.BigDecimal;

public record FoodAnalysisItemResponse(
    String name,
    BigDecimal estimatedGrams,
    BigDecimal calories,
    BigDecimal proteinGrams,
    BigDecimal carbohydrateGrams,
    BigDecimal fatGrams,
    BigDecimal fiberGrams,
    BigDecimal confidence
) {
}
