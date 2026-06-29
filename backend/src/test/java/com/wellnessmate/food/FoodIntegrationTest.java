package com.wellnessmate.food;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FoodIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void searchesBuiltInCatalogAndCalculatesNutrientsByGrams() throws Exception {
    String token = register("foodcatalog");

    mockMvc.perform(get("/api/food/catalog?query=鸡胸")
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Chicken breast / 鸡胸肉"))
        .andExpect(jsonPath("$[0].proteinPer100g").value(31.0));

    Map<String, Object> request = Map.of(
        "recordedAt", Instant.now().minusSeconds(30).toString(),
        "mealType", "LUNCH",
        "items", List.of(
            Map.of("foodId", 1, "grams", 200),
            Map.of("foodId", 8, "grams", 150)),
        "notes", "Lunch");
    String response = mockMvc.perform(post("/api/food/entries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totals.calories").value(525.0))
        .andExpect(jsonPath("$.totals.proteinGrams").value(66.05))
        .andExpect(jsonPath("$.totals.carbohydrateGrams").value(42.3))
        .andExpect(jsonPath("$.totals.fatGrams").value(7.65))
        .andExpect(jsonPath("$.totals.fiberGrams").value(0.6))
        .andExpect(jsonPath("$.mealType").value("LUNCH"))
        .andReturn().getResponse().getContentAsString();
    long foodEntryId = objectMapper.readTree(response).path("id").asLong();

    String from = Instant.now().minus(1, ChronoUnit.DAYS).toString();
    String to = Instant.now().plus(1, ChronoUnit.DAYS).toString();
    mockMvc.perform(get("/api/food/entries").param("from", from).param("to", to)
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(foodEntryId))
        .andExpect(jsonPath("$[0].items.length()").value(2));

    mockMvc.perform(get("/api/tracker-entries?type=FOOD")
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].amount").value(525.0));
  }

  @Test
  void confirmsAnalyzedItemsAndCascadesDelete() throws Exception {
    String token = register("foodanalysis");
    Map<String, Object> request = Map.of(
        "recordedAt", Instant.now().minusSeconds(30).toString(),
        "mealType", "DINNER",
        "items", List.of(Map.of(
            "name", "Estimated meal", "grams", 300, "calories", 450,
            "proteinGrams", 30, "carbohydrateGrams", 40,
            "fatGrams", 18, "fiberGrams", 6)),
        "notes", "Confirmed estimate");
    String response = mockMvc.perform(post("/api/food/entries/analyzed")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("AI"))
        .andExpect(jsonPath("$.mealType").value("DINNER"))
        .andExpect(jsonPath("$.totals.calories").value(450.0))
        .andReturn().getResponse().getContentAsString();
    JsonNode entry = objectMapper.readTree(response);

    mockMvc.perform(delete("/api/food/entries/{id}", entry.path("id").asLong())
            .header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/tracker-entries/{id}", entry.path("trackerEntryId").asLong())
            .header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void photoAnalysisRequiresServerSideApiKey() throws Exception {
    String token = register("foodnokey");
    MockMultipartFile image = new MockMultipartFile(
        "image", "meal.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    mockMvc.perform(multipart("/api/food/analyze").file(image)
            .header("Authorization", bearer(token)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FOOD_AI_NOT_CONFIGURED"));
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
