package com.wellnessmate.plan.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkoutBlockRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String content,
    @Size(max = 500) String imageUrl,
    @Size(max = 500) String videoUrl
) {}
