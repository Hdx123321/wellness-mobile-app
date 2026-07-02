package com.wellnessmate.plan.api;

import java.time.Instant;

public record TrainingPlanResponse(
    Long id, Long coachId, String coachName, String title, String goal, String difficulty,
    int durationWeeks, String summary, String weeklySchedule, String equipment, String safetyNotes, String videoUrl,
    long checkInCount, boolean checkedInToday, Instant createdAt) {}
