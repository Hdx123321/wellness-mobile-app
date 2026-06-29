package com.wellnessmate.food.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.wellnessmate.food.domain.MealType;
import java.time.Instant;
import java.util.List;

public record AnalyzedFoodEntryRequest(
    @NotNull Instant recordedAt,
    @NotNull MealType mealType,
    @NotEmpty @Size(max = 20) List<@Valid AnalyzedFoodItemRequest> items,
    @Size(max = 1000) String notes
) {
}
