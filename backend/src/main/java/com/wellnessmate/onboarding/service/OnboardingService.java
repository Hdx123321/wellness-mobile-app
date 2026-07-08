package com.wellnessmate.onboarding.service;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.onboarding.api.OnboardingRequest;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.domain.UserProfile;
import com.wellnessmate.onboarding.repository.UserProfileRepository;
import com.wellnessmate.tracker.api.TrackerEntryRequest;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.service.TrackerService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes and reads first-login profile data. @author TODO(team member) */
@Service
public class OnboardingService {
  private final UserAccountRepository users;
  private final UserProfileRepository profiles;
  private final TrackerService trackers;

  public OnboardingService(UserAccountRepository users, UserProfileRepository profiles,
                           TrackerService trackers) {
    this.users = users;
    this.profiles = profiles;
    this.trackers = trackers;
  }

  @Transactional
  public ProfileResponse save(Long userId, OnboardingRequest request) {
    if ((request.targetWeightKg() == null) != (request.goalDurationWeeks() == null)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_WEIGHT_GOAL",
          "Target weight and goal duration must be provided together");
    }
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER", "Unknown user"));
    var existing = profiles.findById(userId);
    boolean firstProfileSave = existing.isEmpty();
    UserProfile profile = existing.orElseGet(() -> new UserProfile(userId));
    BigDecimal profileWeight = firstProfileSave ? request.currentWeightKg() : profile.getCurrentWeightKg();
    BigDecimal requestedWeight = request.currentWeightKg();
    boolean currentWeightChanged = !firstProfileSave && requestedWeight != null
        && (profile.getCurrentWeightKg() == null
            || profile.getCurrentWeightKg().compareTo(requestedWeight) != 0);

    profile.update(request.dateOfBirth(), request.heightCm(), profileWeight, request.sex(),
        request.ethnicity(), request.targetWeightKg(), request.goalDurationWeeks(), request.dailyRoutine(),
        request.activityLevel(), request.exercisePreferences(), request.coreNeeds());
    profiles.save(profile);
    if (firstProfileSave || currentWeightChanged) {
      trackers.create(userId, new TrackerEntryRequest(TrackerType.WEIGHT, Instant.now(),
          requestedWeight, null, firstProfileSave
              ? "Initial onboarding weight"
              : "Synced from profile current weight update"));
    }
    user.completeOnboarding();
    return ProfileResponse.from(profile);
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(Long userId) {
    return profiles.findById(userId).map(ProfileResponse::from)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
  }
}
