package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.common.api.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI-compatible embeddings client.
 * Calls {@code POST /v1/embeddings} to produce float vectors for semantic search.
 */
@Service
public class EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final int dimensions;
  private final ObjectMapper mapper;

  public EmbeddingClient(
      @Value("${embedding.base-url:https://api.openai.com/v1}") String baseUrl,
      @Value("${embedding.api-key:}") String apiKey,
      @Value("${embedding.model:text-embedding-3-small}") String model,
      @Value("${embedding.dimensions:512}") int dimensions,
      ObjectMapper mapper) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.dimensions = dimensions;
    this.mapper = mapper;
  }

  /**
   * Returns a {@code dimensions}-dimensional embedding for the given text.
   */
  public float[] embed(String text) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "EMBEDDING_NOT_CONFIGURED", "Embedding service requires EMBEDDING_API_KEY");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("input", text);
    payload.put("dimensions", dimensions);

    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/embeddings").body(payload).retrieve().body(JsonNode.class);
      return parseEmbedding(response);
    } catch (RestClientException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "EMBEDDING_UNAVAILABLE",
          "Embedding service is temporarily unavailable");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "EMBEDDING_INVALID_RESPONSE",
          "Embedding service returned an invalid response");
    }
  }

  /**
   * Batch-embed multiple texts in a single API call.
   */
  public List<float[]> embedBatch(List<String> texts) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "EMBEDDING_NOT_CONFIGURED", "Embedding service requires EMBEDDING_API_KEY");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("input", texts);
    payload.put("dimensions", dimensions);

    try {
      JsonNode response = RestClient.builder().baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build().post().uri("/embeddings").body(payload).retrieve().body(JsonNode.class);
      List<float[]> results = new ArrayList<>();
      JsonNode data = response.path("data");
      for (JsonNode item : data) {
        results.add(parseFloatArray(item.path("embedding")));
      }
      return results;
    } catch (RestClientException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "EMBEDDING_UNAVAILABLE",
          "Embedding service is temporarily unavailable");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "EMBEDDING_INVALID_RESPONSE",
          "Embedding service returned an invalid response");
    }
  }

  private float[] parseEmbedding(JsonNode response) {
    return parseFloatArray(response.path("data").path(0).path("embedding"));
  }

  private float[] parseFloatArray(JsonNode arrayNode) {
    float[] result = new float[arrayNode.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = arrayNode.get(i).floatValue();
    }
    return result;
  }
}
