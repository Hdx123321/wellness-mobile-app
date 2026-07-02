package com.wellnessmate.tracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end tests for built-in tracker types and ownership. @author TODO(team member) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackerIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void catalogExposesNineCanonicalTypes() throws Exception {
    String token = register("cataloguser");

    mockMvc.perform(get("/api/trackers/types").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(9))
        .andExpect(jsonPath("$[?(@.type == 'WEIGHT')].unit").value("kg"))
        .andExpect(jsonPath("$[?(@.type == 'WATER')].unit").value("ml"))
        .andExpect(jsonPath("$[?(@.type == 'MEDICINE')].detailRequired").value(true))
        .andExpect(jsonPath("$[?(@.type == 'HEART_RATE')].unit").value("bpm"))
        .andExpect(jsonPath("$[?(@.type == 'BLOOD_GLUCOSE')].unit").value("mmol/L"));
  }

  @Test
  void createsAndFiltersAllBuiltInTrackerTypes() throws Exception {
    String token = register("alltrackers");
    createFoodEntry(token);
    createEntry(token, "WEIGHT", "72.4", null);
    createEntry(token, "WORKOUT", "45", "Strength training");
    createEntry(token, "STEPS", "8000", null);
    createEntry(token, "SLEEP", "465", null);
    createEntry(token, "WATER", "500", null);
    createEntry(token, "MEDICINE", "1", "Vitamin D");
    createEntry(token, "HEART_RATE", "72", null);
    createEntry(token, "BLOOD_GLUCOSE", "5.6", "Before breakfast");

    mockMvc.perform(get("/api/tracker-entries")
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(9));

    mockMvc.perform(get("/api/tracker-entries?type=STEPS")
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].amount").value(8000.0))
        .andExpect(jsonPath("$.content[0].unit").value("steps"));
  }

  @Test
  void validatesTypeSpecificFields() throws Exception {
    String token = register("validationuser");

    mockMvc.perform(post("/api/tracker-entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson("FOOD", "300", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FOOD_ENTRY_REQUIRES_ITEMS"));

    mockMvc.perform(post("/api/tracker-entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson("STEPS", "12.5", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TRACKER_AMOUNT_MUST_BE_INTEGER"));

    mockMvc.perform(post("/api/tracker-entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson("WEIGHT", "700", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TRACKER_AMOUNT_OUT_OF_RANGE"));

    mockMvc.perform(post("/api/tracker-entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson("MEDICINE", "1", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TRACKER_DETAIL_REQUIRED"));
  }

  @Test
  void updateAndDeleteAreScopedToOwner() throws Exception {
    String ownerToken = register("trackerowner");
    String outsiderToken = register("trackeroutsider");
    long id = createEntry(ownerToken, "WATER", "350", null).path("id").asLong();

    mockMvc.perform(get("/api/tracker-entries/{id}", id)
            .header("Authorization", bearer(outsiderToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACKER_ENTRY_NOT_FOUND"));

    mockMvc.perform(put("/api/tracker-entries/{id}", id)
            .header("Authorization", bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson("WATER", "600", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(600.0));

    mockMvc.perform(delete("/api/tracker-entries/{id}", id)
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/tracker-entries/{id}", id)
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void creatingWeightAgainOnTheSameDayUpdatesTheExistingEntry() throws Exception {
    String token = register("dailyweight");
    JsonNode first = createEntry(token, "WEIGHT", "72.4", null);
    JsonNode second = createEntry(token, "WEIGHT", "71.8", null);

    org.junit.jupiter.api.Assertions.assertEquals(first.path("id").asLong(), second.path("id").asLong());
    mockMvc.perform(get("/api/tracker-entries?type=WEIGHT")
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].amount").value(71.8));
  }

  private JsonNode createEntry(String token, String type, String amount, String detail) throws Exception {
    String response = mockMvc.perform(post("/api/tracker-entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(entryJson(type, amount, detail)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response);
  }

  private void createFoodEntry(String token) throws Exception {
    Map<String, Object> body = Map.of(
        "recordedAt", Instant.now().minusSeconds(60).toString(),
        "mealType", "SNACK",
        "items", java.util.List.of(Map.of("foodId", 1, "grams", 100)),
        "notes", "integration test");
    mockMvc.perform(post("/api/food/entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
  }

  private String entryJson(String type, String amount, String detail) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("recordedAt", Instant.now().minusSeconds(60).toString());
    body.put("amount", new BigDecimal(amount));
    body.put("detail", detail);
    body.put("notes", "integration test");
    return objectMapper.writeValueAsString(body);
  }

  private String register(String username) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "username", username,
        "email", username + "@example.com",
        "password", "StrongPass123",
        "displayName", username));
    String response = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response).path("accessToken").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
