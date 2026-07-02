package com.wellnessmate.plan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.repository.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingPlanIntegrationTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserAccountRepository users;

  @Test
  void coachPublishesAndClientChecksInOncePerDay() throws Exception {
    String client = register("plan-client");
    register("plan-coach");
    UserAccount coach = users.findByUsernameIgnoreCase("plan-coach").orElseThrow();
    coach.promoteToCoach();
    users.flush();
    String coachToken = login("plan-coach");

    mockMvc.perform(post("/api/training-plans").header("Authorization", bearer(client))
            .contentType(MediaType.APPLICATION_JSON).content(planJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COACH_REQUIRED"));

    String created = mockMvc.perform(post("/api/training-plans").header("Authorization", bearer(coachToken))
            .contentType(MediaType.APPLICATION_JSON).content(planJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.coachName").value("plan-coach"))
        .andReturn().getResponse().getContentAsString();
    long id = mapper.readTree(created).path("id").asLong();

    mockMvc.perform(get("/api/training-plans").header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Four-week foundation"));
    mockMvc.perform(post("/api/training-plans/{id}/check-ins", id).header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInToday").value(true))
        .andExpect(jsonPath("$.checkInCount").value(1));
    mockMvc.perform(post("/api/training-plans/{id}/check-ins", id).header("Authorization", bearer(client)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.checkInCount").value(1));
  }

  private String planJson() throws Exception { return mapper.writeValueAsString(Map.of(
      "title", "Four-week foundation", "goal", "Build consistency", "difficulty", "Beginner",
      "durationWeeks", 4, "summary", "Three balanced sessions per week.",
      "weeklySchedule", "Mon - strength\nWed - walk\nFri - mobility",
      "equipment", "Mat", "safetyNotes", "Stop if pain occurs",
      "videoUrl", "https://example.com/workout.mp4")); }
  private String register(String username) throws Exception {
    String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("username", username, "email", username + "@example.com",
            "password", "StrongPass123", "displayName", username))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(response).path("accessToken").asText();
  }
  private String login(String username) throws Exception {
    String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("identifier", username, "password", "StrongPass123"))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(response).path("accessToken").asText();
  }
  private String bearer(String token) { return "Bearer " + token; }
}
