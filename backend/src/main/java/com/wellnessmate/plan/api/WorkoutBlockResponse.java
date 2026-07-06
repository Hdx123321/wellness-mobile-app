package com.wellnessmate.plan.api;

public record WorkoutBlockResponse(
    Long id, int sortOrder, String title, String content, String imageUrl, String videoUrl
) {}
