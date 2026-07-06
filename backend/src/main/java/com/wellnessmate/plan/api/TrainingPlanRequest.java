package com.wellnessmate.plan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrainingPlanRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 120) String goal,
    @NotBlank @Size(max = 30) String difficulty,
    @Min(1) @Max(52) int durationWeeks,
    @NotBlank @Size(max = 1000) String summary,
    @Size(max = 10000) String weeklySchedule,
    @Size(max = 500) String equipment,
    @Size(max = 1000) String safetyNotes,
    @Size(max = 1000) @Pattern(regexp = "^https://.+", message = "must be an HTTPS URL") String videoUrl,
    @NotEmpty @Valid List<WorkoutBlockRequest> blocks
) {}
