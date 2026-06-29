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

/** Server-only OpenAI Responses API client for approximate food-photo analysis. */
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
                           @Value("${ai.base-url:https://api.openai.com/v1}") String baseUrl,
                           @Value("${ai.api-key:}") String apiKey,
                           @Value("${ai.model:gpt-5.5}") String model) {
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

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("store", false);
    payload.put("max_output_tokens", 1200);
    payload.put("input", List.of(Map.of(
        "role", "user",
        "content", List.of(
            Map.of("type", "input_text", "text", prompt()),
            Map.of("type", "input_image", "detail", "high", "image_url",
                "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image))))));
    payload.put("text", Map.of("format", Map.of(
        "type", "json_schema", "name", "food_analysis", "strict", true,
        "schema", schema())));

    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/responses").body(payload).retrieve().body(JsonNode.class);
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
    for (JsonNode output : response.path("output")) {
      for (JsonNode content : output.path("content")) {
        if ("output_text".equals(content.path("type").asText())) {
          return content.path("text").asText();
        }
      }
    }
    return "";
  }

  private BigDecimal decimal(JsonNode node, String field) {
    return node.path(field).decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private String prompt() {
    return """
        Identify only visible foods and drinks in this meal photo. Separate major components,
        estimate edible grams, calories, protein, carbohydrates, fat, and fiber for each component.
        Use realistic prepared-food values. Confidence is from 0 to 1. Do not provide health advice.
        If portions are uncertain, make a conservative estimate and lower confidence.
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
            "items", Map.of("type", "array", "minItems", 1, "maxItems", 20, "items", item)),
        "required", List.of("summary", "items"));
  }
}
