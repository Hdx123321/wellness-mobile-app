package com.wellnessmate.onboarding.api;

import java.util.List;

/** Stable question metadata used to render onboarding controls. @author TODO(team member) */
public record OnboardingQuestion(
    String id,
    String type,
    boolean required,
    String prompt,
    List<String> options
) {
}
