package com.wellnessmate.food.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record FoodEntryRequest(
    @NotNull Instant recordedAt,
    @NotEmpty @Size(max = 20) List<@Valid CatalogFoodItemRequest> items,
    @Size(max = 1000) String notes
) {
}
