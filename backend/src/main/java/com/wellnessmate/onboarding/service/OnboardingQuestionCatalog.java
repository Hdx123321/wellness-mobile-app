package com.wellnessmate.onboarding.service;

import com.wellnessmate.onboarding.api.OnboardingQuestion;
import com.wellnessmate.onboarding.domain.ActivityLevel;
import com.wellnessmate.onboarding.domain.CoreNeed;
import com.wellnessmate.onboarding.domain.DailyRoutine;
import com.wellnessmate.onboarding.domain.EthnicityOption;
import com.wellnessmate.onboarding.domain.ExercisePreference;
import com.wellnessmate.onboarding.domain.SexOption;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/** Version-one onboarding question catalog. @author TODO(team member) */
@Component
public class OnboardingQuestionCatalog {
  public List<OnboardingQuestion> questions() {
    return List.of(
        question("dateOfBirth", "DATE", true, "What is your date of birth?"),
        question("heightCm", "DECIMAL", true, "What is your height in centimetres?"),
        question("currentWeightKg", "DECIMAL", true, "What is your current weight in kilograms?"),
        choice("sex", true, "What was your sex recorded at birth?", SexOption.values()),
        choice("ethnicity", false, "How do you describe your ethnicity? You may skip this.", EthnicityOption.values()),
        question("targetWeightKg", "DECIMAL", false, "Do you have a target weight in kilograms?"),
        question("goalDurationWeeks", "INTEGER", false, "How many weeks do you expect the weight goal to take?"),
        choice("dailyRoutine", true, "Which best describes your usual day?", DailyRoutine.values()),
        choice("activityLevel", true, "How active are you currently?", ActivityLevel.values()),
        choices("exercisePreferences", true, "Which exercise types do you prefer?", ExercisePreference.values()),
        choices("coreNeeds", true, "What do you mainly want WellnessMate to help with?", CoreNeed.values())
    );
  }

  private OnboardingQuestion question(String id, String type, boolean required, String prompt) {
    return new OnboardingQuestion(id, type, required, prompt, List.of());
  }

  private OnboardingQuestion choice(String id, boolean required, String prompt, Enum<?>[] values) {
    return new OnboardingQuestion(id, "SINGLE_CHOICE", required, prompt, names(values));
  }

  private OnboardingQuestion choices(String id, boolean required, String prompt, Enum<?>[] values) {
    return new OnboardingQuestion(id, "MULTIPLE_CHOICE", required, prompt, names(values));
  }

  private List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
