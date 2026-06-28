package com.wellnessmate.onboarding.api;

import com.wellnessmate.onboarding.service.OnboardingQuestionCatalog;
import com.wellnessmate.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated first-login questionnaire and private profile API. @author TODO(team member) */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {
  private final OnboardingQuestionCatalog catalog;
  private final OnboardingService onboarding;

  public OnboardingController(OnboardingQuestionCatalog catalog, OnboardingService onboarding) {
    this.catalog = catalog;
    this.onboarding = onboarding;
  }

  @GetMapping("/questions")
  public List<OnboardingQuestion> questions() {
    return catalog.questions();
  }

  @GetMapping("/profile")
  public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
    return onboarding.get(Long.parseLong(jwt.getSubject()));
  }

  @PutMapping("/profile")
  public ProfileResponse save(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody OnboardingRequest request) {
    return onboarding.save(Long.parseLong(jwt.getSubject()), request);
  }
}
