package com.wellnessmate.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wellnessmate.advisor.service.AiAdvisorClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiAdvisorClientTest {
  @Test
  void sendsChatCompletionsRequestAndParsesResponse() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = """
          {"choices":[{"message":{"content":"Add a short walk after lunch."}}]}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      AiAdvisorClient client = new AiAdvisorClient(
          "http://localhost:" + server.getAddress().getPort() + "/v1", "test-key", "test-model",
          new ObjectMapper());
      String result = client.reply("Private context and a user question");

      assertThat(result).isEqualTo("Add a short walk after lunch.");
      assertThat(requestBody.get()).contains("\"model\":\"test-model\"",
          "Private context and a user question", "non-diagnostic",
          "\"role\":\"system\"", "\"role\":\"user\"", "\"messages\"");
    } finally {
      server.stop(0);
    }
  }
}
