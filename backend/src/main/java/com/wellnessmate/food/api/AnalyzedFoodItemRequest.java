package com.wellnessmate.food.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AnalyzedFoodItemRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal grams,
    @NotNull @DecimalMin("0") @DecimalMax("20000") BigDecimal calories,
    @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal proteinGrams,
    @NotNull @DecimalMin("0") @DecimalMax("2000") BigDecimal carbohydrateGrams,
    @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal fatGrams,
    @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal fiberGrams
) {
}
