package com.wellnessmate.onboarding.api;

import com.wellnessmate.onboarding.domain.ActivityLevel;
import com.wellnessmate.onboarding.domain.CoreNeed;
import com.wellnessmate.onboarding.domain.DailyRoutine;
import com.wellnessmate.onboarding.domain.EthnicityOption;
import com.wellnessmate.onboarding.domain.ExercisePreference;
import com.wellnessmate.onboarding.domain.SexOption;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Answers submitted at the end of first-login onboarding. @author TODO(team member) */
public record OnboardingRequest(
    @NotNull @Past LocalDate dateOfBirth,
    @NotNull @DecimalMin("80.0") @DecimalMax("250.0") BigDecimal heightCm,
    @NotNull @DecimalMin("25.0") @DecimalMax("350.0") BigDecimal currentWeightKg,
    @NotNull SexOption sex,
    EthnicityOption ethnicity,
    @DecimalMin("25.0") @DecimalMax("350.0") BigDecimal targetWeightKg,
    @Min(1) @Max(260) Integer goalDurationWeeks,
    @NotNull DailyRoutine dailyRoutine,
    @NotNull ActivityLevel activityLevel,
    @NotNull Set<ExercisePreference> exercisePreferences,
    @NotNull Set<CoreNeed> coreNeeds
) {
}
