package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.common.api.ApiException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiAdvisorClient {
  private static final Logger log = LoggerFactory.getLogger(AiAdvisorClient.class);
  private static final String SYSTEM_PROMPT = """
      You are a concise wellness advisor. Use only the supplied profile and tracker context.
      Give practical, non-diagnostic guidance. Never prescribe medication, claim diagnosis, or
      replace a clinician. For urgent symptoms advise local emergency care. State uncertainty.
      """;

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final ObjectMapper mapper;

  public AiAdvisorClient(@Value("${ai.base-url:https://api.openai.com/v1}") String baseUrl,
                         @Value("${ai.api-key:}") String apiKey,
                         @Value("${ai.model:gpt-5.5}") String model,
                         ObjectMapper mapper) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.mapper = mapper;
  }

  // Synchronous call — used for tests and fallback.
  public String reply(String input) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ADVISOR_NOT_CONFIGURED",
          "AI advisor requires a server-side LLM_API_KEY");
    }
    Map<String, Object> payload = buildPayload(input);
    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/chat/completions").body(payload).retrieve().body(JsonNode.class);
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

  /**
   * Stream tokens from the AI provider. Calls {@code onToken} for each text chunk,
   * then {@code onComplete} when the stream finishes normally, or {@code onError} on failure.
   * Runs on a virtual thread so the caller is never blocked.
   */
  public void replyStream(String input,
                          Consumer<String> onToken,
                          Consumer<Throwable> onError,
                          Runnable onComplete) {
    if (apiKey == null || apiKey.isBlank()) {
      onError.accept(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "AI_ADVISOR_NOT_CONFIGURED", "AI advisor requires a server-side LLM_API_KEY"));
      return;
    }
    Map<String, Object> payload = buildPayload(input);
    payload.put("stream", true);

    Thread.ofVirtual().start(() -> {
      try {
        String body = mapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpClient client = HttpClient.newBuilder().build();
        HttpResponse<java.io.InputStream> response =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
          String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
          log.error("AI stream error {}: {}", response.statusCode(), errorBody);
          onError.accept(new ApiException(HttpStatus.BAD_GATEWAY,
              "AI_ADVISOR_UNAVAILABLE", "AI advisor is temporarily unavailable"));
          return;
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
              String data = line.substring(6);
              if ("[DONE]".equals(data)) break;
              try {
                JsonNode node = mapper.readTree(data);
                JsonNode delta = node.path("choices").path(0).path("delta");
                if (delta.has("content")) {
                  onToken.accept(delta.path("content").asText());
                }
              } catch (JsonProcessingException ignored) {
                // skip unparseable chunks
              }
            }
          }
        }
        onComplete.run();
      } catch (Exception error) {
        log.error("AI stream failed", error);
        onError.accept(error);
      }
    });
  }

  private Map<String, Object> buildPayload(String input) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("max_tokens", 800);
    payload.put("messages", List.of(
        Map.of("role", "system", "content", SYSTEM_PROMPT),
        Map.of("role", "user", "content", input)));
    return payload;
  }

  private String outputText(JsonNode response) {
    if (response == null) return "";
    return response.path("choices").path(0).path("message").path("content").asText();
  }
}
