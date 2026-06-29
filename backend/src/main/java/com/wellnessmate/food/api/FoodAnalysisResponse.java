package com.wellnessmate.food.api;

import java.util.List;

public record FoodAnalysisResponse(
    String summary,
    List<FoodAnalysisItemResponse> items,
    String disclaimer
) {
}
