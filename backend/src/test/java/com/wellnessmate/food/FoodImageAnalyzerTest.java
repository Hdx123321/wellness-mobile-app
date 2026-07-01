package com.wellnessmate.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wellnessmate.food.api.FoodAnalysisResponse;
import com.wellnessmate.food.service.FoodImageAnalyzer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FoodImageAnalyzerTest {
  @Test
  void sendsImageWithJsonSchemaAndParsesResponse() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/responses", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String analyzed = """
          {"summary":"Rice and chicken","items":[{"name":"Chicken","estimatedGrams":120,
          "calories":198,"proteinGrams":37.2,"carbohydrateGrams":0,"fatGrams":4.3,
          "fiberGrams":0,"confidence":0.86}]}
          """.replace("\n", "");
      String response = new ObjectMapper().writeValueAsString(java.util.Map.of(
          "output", java.util.List.of(java.util.Map.of(
              "type", "message",
              "content", java.util.List.of(java.util.Map.of(
                  "type", "output_text", "text", analyzed))))));
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      FoodImageAnalyzer analyzer = new FoodImageAnalyzer(new ObjectMapper(),
          "http://localhost:" + server.getAddress().getPort() + "/v1", "test-key", "test-model");
      FoodAnalysisResponse result = analyzer.analyze(new byte[] {1, 2, 3}, "image/jpeg");

      assertEquals("Rice and chicken", result.summary());
      assertEquals("Chicken", result.items().getFirst().name());
      assertTrue(requestBody.get().contains("data:image/jpeg;base64,AQID"));
      assertTrue(requestBody.get().contains("json_schema"));
      assertTrue(requestBody.get().contains("\"store\":false"));
      assertTrue(requestBody.get().contains("\"max_output_tokens\":800"));
      assertTrue(requestBody.get().contains("\"detail\":\"low\""));
      assertTrue(requestBody.get().contains("\"maxItems\":10"));
    } finally {
      server.stop(0);
    }
  }
}
