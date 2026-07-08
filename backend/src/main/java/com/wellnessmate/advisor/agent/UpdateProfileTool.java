package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.onboarding.api.OnboardingRequest;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.domain.ActivityLevel;
import com.wellnessmate.onboarding.domain.DailyRoutine;
import com.wellnessmate.onboarding.service.OnboardingService;
import com.wellnessmate.tracker.api.TrackerEntryRequest;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.service.TrackerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Updates low-risk profile fields while preserving the rest of the onboarding profile.
 */
@Component
class UpdateProfileTool implements Tool {

  private final OnboardingService onboarding;
  private final TrackerService tracker;

  UpdateProfileTool(OnboardingService onboarding, TrackerService tracker) {
    this.onboarding = onboarding;
    this.tracker = tracker;
  }

  @Override
  public String name() {
    return "update_profile";
  }

  @Override
  public String description() {
    return "Update the user's profile fields such as height, current weight, target weight, "
        + "goal duration, daily routine, or activity level. Current weight is logged as a WEIGHT "
        + "tracker entry; the profile's starting weight baseline is preserved.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> height = numberField("Height in centimeters, e.g. 188");
    Map<String, Object> currentWeight = numberField("Current body weight in kg. This logs a WEIGHT tracker entry and does not change startingWeight.");
    Map<String, Object> targetWeight = numberField("Target body weight in kg. Use null to keep unchanged.");
    Map<String, Object> goalWeeks = new LinkedHashMap<>();
    goalWeeks.put("type", "integer");
    goalWeeks.put("description", "Goal duration in weeks. Required when changing target_weight_kg.");

    Map<String, Object> dailyRoutine = new LinkedHashMap<>();
    dailyRoutine.put("type", "string");
    dailyRoutine.put("description", "Typical daily movement pattern");
    dailyRoutine.put("enum", List.of("MOSTLY_SITTING", "MOSTLY_STANDING", "MIXED", "PHYSICALLY_DEMANDING"));

    Map<String, Object> activityLevel = new LinkedHashMap<>();
    activityLevel.put("type", "string");
    activityLevel.put("description", "Self-reported activity level");
    activityLevel.put("enum", List.of("LOW", "MODERATE", "HIGH"));

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("height_cm", height);
    props.put("current_weight_kg", currentWeight);
    props.put("target_weight_kg", targetWeight);
    props.put("goal_duration_weeks", goalWeeks);
    props.put("daily_routine", dailyRoutine);
    props.put("activity_level", activityLevel);

    schema.put("properties", props);
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    if (args == null || args.isNull() || args.size() == 0) {
      return "Error: provide at least one profile field to update";
    }

    ProfileResponse current;
    try {
      current = onboarding.get(userId);
    } catch (Exception e) {
      return "Error reading profile: " + e.getMessage();
    }

    BigDecimal height;
    BigDecimal requestedCurrentWeight;
    BigDecimal targetWeight;
    Integer goalWeeks;
    DailyRoutine dailyRoutine;
    ActivityLevel activityLevel;
    try {
      height = decimalOrCurrent(args, "height_cm", current.heightCm());
      requestedCurrentWeight = decimalOrCurrent(args, "current_weight_kg", current.currentWeightKg());
      targetWeight = decimalOrCurrent(args, "target_weight_kg", current.targetWeightKg());
      goalWeeks = args.has("goal_duration_weeks") && !args.path("goal_duration_weeks").isNull()
          ? args.path("goal_duration_weeks").asInt()
          : current.goalDurationWeeks();
      dailyRoutine = enumOrCurrent(args, "daily_routine", DailyRoutine.class,
          current.dailyRoutine());
      activityLevel = enumOrCurrent(args, "activity_level", ActivityLevel.class,
          current.activityLevel());
    } catch (IllegalArgumentException e) {
      return "Error: invalid profile update value. " + e.getMessage();
    }

    String validation = validate(height, requestedCurrentWeight, targetWeight, goalWeeks);
    if (validation != null) {
      return validation;
    }

    try {
      boolean currentWeightSupplied = args.has("current_weight_kg")
          && !args.path("current_weight_kg").isNull();
      ProfileResponse updated = onboarding.save(userId, new OnboardingRequest(
          current.dateOfBirth(),
          height,
          current.currentWeightKg(),
          current.sex(),
          current.ethnicity(),
          targetWeight,
          goalWeeks,
          dailyRoutine,
          activityLevel,
          current.exercisePreferences(),
          current.coreNeeds()));
      String trackerResult = "";
      if (currentWeightSupplied) {
        var weightEntry = tracker.create(userId, new TrackerEntryRequest(
            TrackerType.WEIGHT,
            Instant.now(),
            requestedCurrentWeight,
            null,
            "Logged from AI advisor current weight update"));
        trackerResult = ", loggedWeightEntry=#" + weightEntry.id();
      }
      return "Updated profile: height=" + updated.heightCm() + " cm, startingWeight="
          + updated.currentWeightKg() + " kg, targetWeight=" + updated.targetWeightKg()
          + " kg, activityLevel=" + updated.activityLevel() + trackerResult + ".";
    } catch (Exception e) {
      return "Error updating profile: " + e.getMessage();
    }
  }

  private Map<String, Object> numberField(String description) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("type", "number");
    field.put("description", description);
    return field;
  }

  private BigDecimal decimalOrCurrent(JsonNode args, String field, BigDecimal current) {
    if (!args.has(field) || args.path(field).isNull()) {
      return current;
    }
    return new BigDecimal(args.path(field).asText()).setScale(2, RoundingMode.HALF_UP);
  }

  private <T extends Enum<T>> T enumOrCurrent(JsonNode args, String field, Class<T> type, T current) {
    if (!args.has(field) || args.path(field).isNull() || args.path(field).asText().isBlank()) {
      return current;
    }
    return Enum.valueOf(type, args.path(field).asText().toUpperCase());
  }

  private String validate(BigDecimal height, BigDecimal currentWeight, BigDecimal targetWeight,
                          Integer goalWeeks) {
    if (height == null || height.compareTo(new BigDecimal("80.0")) < 0
        || height.compareTo(new BigDecimal("250.0")) > 0) {
      return "Error: height_cm must be between 80 and 250 cm";
    }
    if (currentWeight == null || currentWeight.compareTo(new BigDecimal("25.0")) < 0
        || currentWeight.compareTo(new BigDecimal("350.0")) > 0) {
      return "Error: current_weight_kg must be between 25 and 350 kg";
    }
    if (targetWeight != null) {
      if (targetWeight.compareTo(new BigDecimal("25.0")) < 0
          || targetWeight.compareTo(new BigDecimal("350.0")) > 0) {
        return "Error: target_weight_kg must be between 25 and 350 kg";
      }
      if (goalWeeks == null || goalWeeks < 1 || goalWeeks > 260) {
        return "Error: goal_duration_weeks must be between 1 and 260 when target_weight_kg is set";
      }
    }
    return null;
  }
}
