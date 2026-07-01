package com.wellnessmate.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.food.api.FoodAnalysisItemResponse;
import com.wellnessmate.food.api.FoodAnalysisResponse;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** OpenAI Chat Completions API client for approximate food-photo analysis. */
@Service
public class FoodImageAnalyzer {
  private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
  private static final String DISCLAIMER =
      "AI estimates can be wrong. Review foods and portions before saving; values are not medical advice.";

  private final ObjectMapper mapper;
  private final String baseUrl;
  private final String apiKey;
  private final String model;

  public FoodImageAnalyzer(ObjectMapper mapper,
                           @Value("${ai.food-base-url:${ai.base-url:https://api.openai.com/v1}}") String baseUrl,
                           @Value("${ai.food-api-key:${ai.api-key:}}") String apiKey,
                           @Value("${ai.food-model:${ai.model:gpt-5.5}}") String model) {
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
  }

  public FoodAnalysisResponse analyze(byte[] image, String contentType) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FOOD_AI_NOT_CONFIGURED",
          "Food photo analysis requires a server-side LLM_API_KEY");
    }
    if (image.length == 0 || image.length > MAX_IMAGE_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOOD_IMAGE",
          "Image must be between 1 byte and 10 MB");
    }
    String mime = contentType == null ? "image/jpeg" : contentType.toLowerCase();
    if (!mime.startsWith("image/")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOOD_IMAGE", "A valid image is required");
    }

    String imageUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("max_tokens", 800);
    payload.put("messages", List.of(Map.of(
        "role", "user",
        "content", List.of(
            Map.of("type", "text", "text", prompt()),
            Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))))));
    payload.put("response_format", Map.of(
        "type", "json_schema",
        "json_schema", Map.of(
            "name", "food_analysis", "strict", true,
            "schema", schema())));

    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/chat/completions").body(payload).retrieve().body(JsonNode.class);
      String output = outputText(response);
      JsonNode parsed = mapper.readTree(output);
      List<FoodAnalysisItemResponse> items = new java.util.ArrayList<>();
      parsed.path("items").forEach(item -> items.add(new FoodAnalysisItemResponse(
          item.path("name").asText(), decimal(item, "estimatedGrams"), decimal(item, "calories"),
          decimal(item, "proteinGrams"), decimal(item, "carbohydrateGrams"),
          decimal(item, "fatGrams"), decimal(item, "fiberGrams"), decimal(item, "confidence"))));
      if (items.isEmpty()) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FOOD_NOT_RECOGNIZED",
            "No food could be recognized in the image");
      }
      return new FoodAnalysisResponse(parsed.path("summary").asText(), items, DISCLAIMER);
    } catch (ApiException error) {
      throw error;
    } catch (RestClientException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "FOOD_AI_UNAVAILABLE",
          "Food photo analysis is temporarily unavailable");
    } catch (Exception error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "FOOD_AI_INVALID_RESPONSE",
          "Food photo analysis returned an invalid result");
    }
  }

  private String outputText(JsonNode response) {
    if (response == null) return "";
    return response.path("choices").path(0).path("message").path("content").asText();
  }

  private BigDecimal decimal(JsonNode node, String field) {
    return node.path(field).decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private String prompt() {
    return """
        Identify visible foods and drinks only. Return concise JSON with major components,
        edible grams, calories, protein, carbohydrates, fat, fiber, and confidence from 0 to 1.
        Use conservative portion estimates when uncertain. Do not provide health advice.
        """;
  }

  private Map<String, Object> schema() {
    Map<String, Object> number = Map.of("type", "number", "minimum", 0);
    Map<String, Object> itemProperties = new LinkedHashMap<>();
    itemProperties.put("name", Map.of("type", "string"));
    itemProperties.put("estimatedGrams", Map.of("type", "number", "minimum", 1, "maximum", 5000));
    itemProperties.put("calories", Map.of("type", "number", "minimum", 0, "maximum", 20000));
    itemProperties.put("proteinGrams", number);
    itemProperties.put("carbohydrateGrams", number);
    itemProperties.put("fatGrams", number);
    itemProperties.put("fiberGrams", number);
    itemProperties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
    Map<String, Object> item = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", itemProperties,
        "required", List.of("name", "estimatedGrams", "calories", "proteinGrams",
            "carbohydrateGrams", "fatGrams", "fiberGrams", "confidence"));
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
            "summary", Map.of("type", "string"),
            "items", Map.of("type", "array", "minItems", 1, "maxItems", 10, "items", item)),
        "required", List.of("summary", "items"));
  }
}
