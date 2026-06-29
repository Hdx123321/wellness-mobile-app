package com.wellnessmate.food.api;

import com.wellnessmate.food.domain.FoodEntry;
import com.wellnessmate.food.domain.FoodEntryItem;
import java.time.Instant;
import java.util.List;

public record FoodEntryResponse(
    Long id,
    Long trackerEntryId,
    Instant recordedAt,
    String source,
    String notes,
    List<FoodEntryItemResponse> items,
    FoodNutrients totals
) {
  public static FoodEntryResponse from(FoodEntry entry, List<FoodEntryItem> items,
                                       FoodNutrients totals) {
    return new FoodEntryResponse(entry.getId(), entry.getTrackerEntryId(), entry.getRecordedAt(),
        entry.getSource().name(), entry.getNotes(),
        items.stream().map(FoodEntryItemResponse::from).toList(), totals);
  }
}
