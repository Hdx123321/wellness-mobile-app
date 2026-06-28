package com.wellnessmate.onboarding.service;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.onboarding.api.OnboardingRequest;
import com.wellnessmate.onboarding.api.ProfileResponse;
import com.wellnessmate.onboarding.domain.UserProfile;
import com.wellnessmate.onboarding.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes and reads first-login profile data. @author TODO(team member) */
@Service
public class OnboardingService {
  private final UserAccountRepository users;
  private final UserProfileRepository profiles;

  public OnboardingService(UserAccountRepository users, UserProfileRepository profiles) {
    this.users = users;
    this.profiles = profiles;
  }

  @Transactional
  public ProfileResponse save(Long userId, OnboardingRequest request) {
    if ((request.targetWeightKg() == null) != (request.goalDurationWeeks() == null)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_WEIGHT_GOAL",
          "Target weight and goal duration must be provided together");
    }
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER", "Unknown user"));
    UserProfile profile = profiles.findById(userId).orElseGet(() -> new UserProfile(userId));
    profile.update(request.dateOfBirth(), request.heightCm(), request.currentWeightKg(), request.sex(),
        request.ethnicity(), request.targetWeightKg(), request.goalDurationWeeks(), request.dailyRoutine(),
        request.activityLevel(), request.exercisePreferences(), request.coreNeeds());
    profiles.save(profile);
    user.completeOnboarding();
    return ProfileResponse.from(profile);
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(Long userId) {
    return profiles.findById(userId).map(ProfileResponse::from)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
  }
}
