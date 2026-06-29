package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.common.api.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiAdvisorClient {
  private final String baseUrl;
  private final String apiKey;
  private final String model;

  public AiAdvisorClient(@Value("${ai.base-url:https://api.openai.com/v1}") String baseUrl,
                         @Value("${ai.api-key:}") String apiKey,
                         @Value("${ai.model:gpt-5.5}") String model) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
  }

  public String reply(String input) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ADVISOR_NOT_CONFIGURED",
          "AI advisor requires a server-side LLM_API_KEY");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("store", false);
    payload.put("max_output_tokens", 800);
    payload.put("instructions", """
        You are a concise wellness advisor. Use only the supplied profile and tracker context.
        Give practical, non-diagnostic guidance. Never prescribe medication, claim diagnosis, or
        replace a clinician. For urgent symptoms advise local emergency care. State uncertainty.
        """);
    payload.put("input", input);
    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/responses").body(payload).retrieve().body(JsonNode.class);
      String output = outputText(response);
      if (output.isBlank()) throw new IllegalStateException("No output text");
      return output.trim();
    } catch (RestClientException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ADVISOR_UNAVAILABLE",
          "AI advisor is temporarily unavailable");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ADVISOR_INVALID_RESPONSE",
          "AI advisor returned an invalid response");
    }
  }

  private String outputText(JsonNode response) {
    if (response == null) return "";
    for (JsonNode output : response.path("output")) {
      for (JsonNode content : output.path("content")) {
        if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
      }
    }
    return "";
  }
}
