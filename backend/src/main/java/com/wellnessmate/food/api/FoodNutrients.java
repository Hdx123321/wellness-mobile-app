package com.wellnessmate.food.api;

import java.math.BigDecimal;

public record FoodNutrients(
    BigDecimal calories,
    BigDecimal proteinGrams,
    BigDecimal carbohydrateGrams,
    BigDecimal fatGrams,
    BigDecimal fiberGrams
) {
}
