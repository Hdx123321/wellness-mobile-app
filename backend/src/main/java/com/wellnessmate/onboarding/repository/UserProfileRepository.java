package com.wellnessmate.onboarding.repository;

import com.wellnessmate.onboarding.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** User profile persistence boundary. @author TODO(team member) */
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
