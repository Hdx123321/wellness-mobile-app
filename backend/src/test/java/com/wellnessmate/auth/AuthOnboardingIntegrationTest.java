package com.wellnessmate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end API tests for registration, login, and first-login onboarding. @author TODO(team member) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthOnboardingIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserAccountRepository users;

  @Test
  void registrationHashesPasswordAndRequiresOnboarding() throws Exception {
    JsonNode response = register("alice", "alice@example.com");

    assertThat(response.path("onboardingRequired").asBoolean()).isTrue();
    assertThat(response.path("role").asText()).isEqualTo("CLIENT");
    assertThat(response.path("accessToken").asText()).isNotBlank();
    assertThat(users.findByUsernameIgnoreCase("alice").orElseThrow().getPasswordHash())
        .isNotEqualTo("StrongPass123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"identifier":"alice@example.com","password":"StrongPass123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingRequired").value(true));
  }

  @Test
  void completingProfileClearsFirstLoginRequirement() throws Exception {
    String token = register("bob", "bob@example.com").path("accessToken").asText();

    mockMvc.perform(get("/api/onboarding/questions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("dateOfBirth"))
        .andExpect(jsonPath("$[?(@.id == 'coreNeeds')].type").value("MULTIPLE_CHOICE"));

    mockMvc.perform(put("/api/onboarding/profile")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(validProfileJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentWeightKg").value(78.5))
        .andExpect(jsonPath("$.exercisePreferences[0]").exists());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"identifier":"bob","password":"StrongPass123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingRequired").value(false));
  }

  @Test
  void profileEndpointIsAlwaysScopedToTokenOwner() throws Exception {
    String aliceToken = register("owner", "owner@example.com").path("accessToken").asText();
    String outsiderToken = register("outsider", "outsider@example.com").path("accessToken").asText();

    mockMvc.perform(put("/api/onboarding/profile")
            .header("Authorization", bearer(aliceToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(validProfileJson()))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/onboarding/profile").header("Authorization", bearer(outsiderToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));

    mockMvc.perform(get("/api/onboarding/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void incompleteWeightGoalReturnsStableError() throws Exception {
    String token = register("goaluser", "goal@example.com").path("accessToken").asText();
    String invalid = validProfileJson().replace("\"goalDurationWeeks\":24", "\"goalDurationWeeks\":null");

    mockMvc.perform(put("/api/onboarding/profile")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalid))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INCOMPLETE_WEIGHT_GOAL"));
  }

  private JsonNode register(String username, String email) throws Exception {
    String body = objectMapper.writeValueAsString(java.util.Map.of(
        "username", username,
        "email", email,
        "password", "StrongPass123",
        "displayName", username));
    String response = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response);
  }

  private String validProfileJson() {
    return """
        {
          "dateOfBirth":"1995-05-12",
          "heightCm":175.0,
          "currentWeightKg":78.5,
          "sex":"PREFER_NOT_TO_SAY",
          "ethnicity":"PREFER_NOT_TO_SAY",
          "targetWeightKg":70.0,
          "goalDurationWeeks":24,
          "dailyRoutine":"MOSTLY_SITTING",
          "activityLevel":"MODERATE",
          "exercisePreferences":["WALKING","STRENGTH"],
          "coreNeeds":["FIND_COACH","TRACK_EXERCISE"]
        }
        """;
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
