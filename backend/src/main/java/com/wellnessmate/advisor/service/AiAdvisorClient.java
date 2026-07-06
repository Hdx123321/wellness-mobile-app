package com.wellnessmate.advisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.agent.ToolCall;
import com.wellnessmate.common.api.ApiException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
  static final String SYSTEM_PROMPT = """
      You are a concise wellness advisor. Use only the supplied profile and tracker context.
      Give practical, non-diagnostic guidance. Never prescribe medication, claim diagnosis, or
      replace a clinician. For urgent symptoms advise local emergency care. State uncertainty.

      You have access to tools for reading and writing the user's health tracker data.
      When the user asks about their data, use query_tracker_data first.
      For recording new data, use create_tracker_entry directly.
      For updating or deleting entries, always tell the user what you plan to do
      and wait for explicit confirmation before calling update_tracker_entry or delete_tracker_entry.
      When referencing entries, always include their ID number so the user can verify.
      """;

  static final String AGENT_SYSTEM_PROMPT = SYSTEM_PROMPT + """

      CRITICAL -- You MUST use tools for these actions. Never just talk about doing them:
      - When the user asks to record, log, save, or track something -> call create_tracker_entry
        (for health metrics) or create_food_entry (for meals/food). Do NOT say "I've noted that"
        without actually calling the tool.
      - When the user asks about their data, history, stats, or trends -> call query_tracker_data.
        Do NOT rely only on context; always get fresh data.
      - When the user asks to change or fix an entry -> first query to find the entry ID,
        then tell the user which entry you plan to update and wait for confirmation,
        then call update_tracker_entry (for food entries: delete + create_food_entry instead).
      - When the user asks to remove or delete an entry -> first query to find the entry ID,
        then tell the user which entry you plan to delete and wait for confirmation,
        then call delete_tracker_entry.
      - If the user's request is ambiguous (e.g. "change my sleep"), ask which specific
        entry or what the new value should be. Do not guess.
      - When you receive tool results, use them to formulate your answer. Do not ask the
        user to look up data you just received.
      - If a tool returns an error, explain the problem to the user and suggest a fix.

      FOOD TRACKER -- use create_food_entry for each food item:
      - create_food_entry needs ONLY: meal_type (BREAKFAST/LUNCH/DINNER/SNACK), food_name, calories.
        grams, protein_grams, carbs_grams, fat_grams are OPTIONAL -- omit if unknown.
      - Call it ONCE per food item. Two foods? Two calls. Same meal_type if part of same meal.
      - Estimate calories from common portions: bowl of rice ~ 260kcal, chicken breast 150g ~ 250kcal,
        apple ~ 95kcal, egg ~ 70kcal.
      - To delete: query data -> look for "use entry_id=N" in the result -> use THAT number
        for delete_tracker_entry. Do NOT use the FoodEntry # number -- use the entry_id shown.
      - To modify: confirm, then delete old + create_food_entry new.
      - NEVER retry the same delete twice. If it returns "not found", tell the user it was
        already deleted. If it succeeds, do NOT query and delete again.
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

  // ── Legacy wrappers (delegate to chatWithTools) ──

  /** Synchronous call -- used by DocumentGenerationService. */
  public String reply(String input) {
    return reply(null, input);
  }

  /** Synchronous call with optional RAG context. */
  public String reply(String ragContext, String input) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ADVISOR_NOT_CONFIGURED",
          "AI advisor requires a server-side LLM_API_KEY");
    }
    List<Map<String, Object>> messages = buildMessages(input, ragContext);
    Map<String, Object> payload = buildPayload(messages, null);
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

  /** Stream tokens -- legacy wrapper, no tools. */
  public void replyStream(String input,
                          Consumer<String> onToken,
                          Consumer<Throwable> onError,
                          Runnable onComplete) {
    replyStream(null, input, onToken, onError, onComplete);
  }

  /** Stream with optional RAG context -- legacy wrapper, no tools. */
  public void replyStream(String ragContext, String input,
                          Consumer<String> onToken,
                          Consumer<Throwable> onError,
                          Runnable onComplete) {
    List<Map<String, Object>> messages = buildMessages(input, ragContext);
    chatWithTools(messages, null,
        token -> {},  // onThinkingToken -- no-op for legacy path
        onToken,
        toolCalls -> onError.accept(
            new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_TOOL_CALL",
                "Model returned tool calls but none were requested")),
        onError,
        onComplete);
  }

  // ── Agent-capable streaming ──

  /**
   * Stream a chat completion that may include tool calls.
   *
   * @param messages       full message history
   * @param tools          OpenAI-compatible {@code tools} payload, or {@code null}/empty
   * @param onThinkingToken called for each {@code delta.reasoning_content} token
   * @param onToken        called for each {@code delta.content} text token
   * @param onToolCalls    called when finish_reason=tool_calls
   * @param onError        called on any failure
   * @param onComplete     called when the stream finishes normally
   */
  public void chatWithTools(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            Consumer<String> onThinkingToken,
                            Consumer<String> onToken,
                            Consumer<List<ToolCall>> onToolCalls,
                            Consumer<Throwable> onError,
                            Runnable onComplete) {
    if (apiKey == null || apiKey.isBlank()) {
      onError.accept(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "AI_ADVISOR_NOT_CONFIGURED", "AI advisor requires a server-side LLM_API_KEY"));
      return;
    }
    Map<String, Object> payload = buildPayload(messages, tools);
    payload.put("stream", true);
    // DeepSeek reasoning models need headroom for thinking tokens + tool_calls
    payload.put("max_tokens", 2400);

    boolean hasTools = tools != null && !tools.isEmpty();
    log.info("chatWithTools: {} tools, {} messages", tools != null ? tools.size() : 0, messages.size());

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

        // Accumulated tool calls keyed by index
        Map<Integer, AccumulatedToolCall> acc = new LinkedHashMap<>();
        StringBuilder finishReason = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
              String data = line.substring(6);
              if ("[DONE]".equals(data)) break;
              try {
                JsonNode node = mapper.readTree(data);
                JsonNode choices = node.path("choices");
                if (choices.isEmpty()) continue;
                JsonNode choice = choices.get(0);

                // Track finish_reason
                if (choice.has("finish_reason") && !choice.path("finish_reason").isNull()) {
                  finishReason.setLength(0);
                  finishReason.append(choice.path("finish_reason").asText());
                }

                JsonNode delta = choice.path("delta");

                // Reasoning / thinking content (DeepSeek reasoning models)
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                  String token = delta.path("reasoning_content").asText();
                  if (!token.isEmpty()) onThinkingToken.accept(token);
                }

                // Text content (the actual reply)
                if (delta.has("content") && !delta.get("content").isNull()) {
                  String token = delta.path("content").asText();
                  if (!token.isEmpty()) onToken.accept(token);
                }

                // Tool calls (only accumulated when tools are provided)
                if (hasTools && delta.has("tool_calls")) {
                  for (JsonNode tc : delta.path("tool_calls")) {
                    int idx = tc.path("index").asInt(0);
                    AccumulatedToolCall call = acc.computeIfAbsent(idx,
                        k -> new AccumulatedToolCall());
                    if (tc.has("id") && !tc.get("id").isNull())
                      call.id = tc.path("id").asText();
                    JsonNode fn = tc.path("function");
                    if (fn.has("name") && !fn.get("name").isNull())
                      call.name = fn.path("name").asText();
                    if (fn.has("arguments") && !fn.get("arguments").isNull())
                      call.args.append(fn.path("arguments").asText());
                  }
                }
              } catch (JsonProcessingException ignored) {
                // skip unparseable chunks
              }
            }
          }
        }

        // Determine outcome
        if ("tool_calls".equals(finishReason.toString()) && !acc.isEmpty()) {
          List<ToolCall> parsed = new ArrayList<>();
          for (AccumulatedToolCall a : acc.values()) {
            if (a.id != null && a.name != null) {
              parsed.add(new ToolCall(a.id, a.name, a.args.toString()));
            }
          }
          if (!parsed.isEmpty()) {
            onToolCalls.accept(parsed);
            return;
          }
        }
        onComplete.run();
      } catch (Exception error) {
        log.error("AI stream failed", error);
        onError.accept(error);
      }
    });
  }

  // ── Payload helpers ──

  private Map<String, Object> buildPayload(List<Map<String, Object>> messages,
                                           List<Map<String, Object>> tools) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("messages", messages);
    if (tools != null && !tools.isEmpty()) {
      payload.put("tools", tools);
      payload.put("tool_choice", "auto");
    }
    return payload;
  }

  private List<Map<String, Object>> buildMessages(String input, String ragContext) {
    var messages = new ArrayList<Map<String, Object>>();
    messages.add(Map.of("role", "system", "content", (Object) SYSTEM_PROMPT));
    if (ragContext != null && !ragContext.isBlank()) {
      messages.add(Map.of("role", "user", "content", (Object) ragContext));
    }
    messages.add(Map.of("role", "user", "content", (Object) input));
    return messages;
  }

  private String outputText(JsonNode response) {
    if (response == null) return "";
    return response.path("choices").path(0).path("message").path("content").asText();
  }

  /** Mutable accumulator for streaming tool call deltas. */
  private static class AccumulatedToolCall {
    String id;
    String name;
    final StringBuilder args = new StringBuilder();
  }
}
