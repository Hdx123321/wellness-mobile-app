package com.wellnessmate.app.ui

import com.wellnessmate.app.data.ProfileResponse
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthProfileViewModelTest {
    @Test
    fun calculatesMetricsFromLatestWeightAndProfile() {
        val profile = ProfileResponse(
            userId = 1,
            dateOfBirth = LocalDate.now().minusYears(30).toString(),
            heightCm = 175.0,
            currentWeightKg = 80.0,
            sex = "MALE",
            ethnicity = null,
            targetWeightKg = 60.0,
            goalStartedAt = "2026-01-01",
            goalDurationWeeks = 20,
            dailyRoutine = "MOSTLY_SITTING",
            activityLevel = "MODERATE",
            exercisePreferences = setOf("WALKING"),
            coreNeeds = setOf("TRACK_EXERCISE"),
        )

        val metrics = calculateHealthMetrics(profile, latestWeightKg = 70.0)

        assertEquals(22.9, metrics.bmi, 0.1)
        assertEquals("1649 kcal/day", metrics.basalMetabolismText)
        assertEquals("95–133 bpm", metrics.fatBurningHeartRateText)
        assertEquals(0.5, metrics.goalProgress ?: 0.0, 0.001)
    }
}
