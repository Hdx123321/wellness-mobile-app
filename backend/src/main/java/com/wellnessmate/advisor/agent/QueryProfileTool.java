package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.service.OnboardingService;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads the current onboarding profile from the database.
 */
@Component
class QueryProfileTool implements Tool {

  private final OnboardingService onboarding;
  private final TrackerEntryRepository trackerEntries;

  QueryProfileTool(OnboardingService onboarding, TrackerEntryRepository trackerEntries) {
    this.onboarding = onboarding;
    this.trackerEntries = trackerEntries;
  }

  @Override
  public String name() {
    return "query_profile";
  }

  @Override
  public String description() {
    return "Read the user's current profile facts, including height, current weight, target weight, activity level, and daily routine.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", Map.of());
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    try {
      ProfileResponse profile = onboarding.get(userId);
      var latestWeight = trackerEntries
          .findFirstByUserIdAndTrackerTypeOrderByRecordedAtDescIdDesc(userId, TrackerType.WEIGHT);
      return "Current profile: heightCm=" + profile.heightCm()
          + ", latestWeightKg=" + latestWeight.map(entry -> entry.getAmount().toString()).orElse("none")
          + ", startingWeightKg=" + profile.currentWeightKg()
          + ", targetWeightKg=" + profile.targetWeightKg()
          + ", goalDurationWeeks=" + profile.goalDurationWeeks()
          + ", dailyRoutine=" + profile.dailyRoutine()
          + ", activityLevel=" + profile.activityLevel() + ".";
    } catch (Exception e) {
      return "Error reading profile: " + e.getMessage();
    }
  }
}
