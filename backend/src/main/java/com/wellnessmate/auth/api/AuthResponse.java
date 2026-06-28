package com.wellnessmate.auth.api;

import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.domain.UserRole;

/** Authentication result consumed by Android session storage. @author TODO(team member) */
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    Long userId,
    String username,
    String displayName,
    UserRole role,
    boolean onboardingRequired
) {
  public static AuthResponse from(UserAccount user, String token, long expiresInSeconds) {
    return new AuthResponse(token, "Bearer", expiresInSeconds, user.getId(), user.getUsername(),
        user.getDisplayName(), user.getRole(), !user.isOnboardingCompleted());
  }
}
