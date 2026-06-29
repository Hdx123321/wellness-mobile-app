package com.wellnessmate.food.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CatalogFoodItemRequest(
    @NotNull Long foodId,
    @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal grams
) {
}
