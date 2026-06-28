package com.wellnessmate.onboarding.api;

import com.wellnessmate.onboarding.domain.ActivityLevel;
import com.wellnessmate.onboarding.domain.CoreNeed;
import com.wellnessmate.onboarding.domain.DailyRoutine;
import com.wellnessmate.onboarding.domain.EthnicityOption;
import com.wellnessmate.onboarding.domain.ExercisePreference;
import com.wellnessmate.onboarding.domain.SexOption;
import com.wellnessmate.onboarding.domain.UserProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Private profile returned only to its authenticated owner. @author TODO(team member) */
public record ProfileResponse(
    Long userId,
    LocalDate dateOfBirth,
    BigDecimal heightCm,
    BigDecimal currentWeightKg,
    SexOption sex,
    EthnicityOption ethnicity,
    BigDecimal targetWeightKg,
    LocalDate goalStartedAt,
    Integer goalDurationWeeks,
    DailyRoutine dailyRoutine,
    ActivityLevel activityLevel,
    Set<ExercisePreference> exercisePreferences,
    Set<CoreNeed> coreNeeds
) {
  public static ProfileResponse from(UserProfile profile) {
    return new ProfileResponse(profile.getUserId(), profile.getDateOfBirth(), profile.getHeightCm(),
        profile.getCurrentWeightKg(), profile.getSex(), profile.getEthnicity(),
        profile.getTargetWeightKg(), profile.getGoalStartedAt(), profile.getGoalDurationWeeks(),
        profile.getDailyRoutine(), profile.getActivityLevel(), profile.getExercisePreferences(),
        profile.getCoreNeeds());
  }
}
