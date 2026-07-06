package com.wellnessmate.plan.api;

import java.time.Instant;
import java.util.List;

public record TrainingPlanResponse(
    Long id, Long coachId, String coachName, String title, String goal, String difficulty,
    int durationWeeks, String summary, String weeklySchedule, String equipment, String safetyNotes, String videoUrl,
    List<WorkoutBlockResponse> blocks,
    long checkInCount, boolean checkedInToday, boolean subscribed, Instant createdAt) {}
