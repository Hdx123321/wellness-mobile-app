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
      Format answers in clean Markdown. Use short paragraphs, bullet lists for steps, and valid
      Markdown tables for multi-row data such as logged foods, tracker entries, comparisons, or
      summaries. A table must include a header row, a separator row, and one row per item.

      You have access to tools for reading and writing the user's health tracker data and profile.
      When the user asks about their data, use query_tracker_data first.
      When the user asks about current profile facts such as height, current weight, target
      weight, activity level, or daily routine, use query_profile first. Current profile data
      is more authoritative than old conversation history. Current weight comes from the
      latest WEIGHT tracker entry; profile startingWeight is only the onboarding/goal baseline.
      For recording new data, use create_tracker_entry directly.
      For changing profile facts such as height, current weight, target weight, activity level,
      or daily routine, use update_profile. Do not tell the user to go to settings for those.
      When the user changes current weight, update_profile logs a WEIGHT tracker entry; it does
      not change the profile startingWeight baseline. Do not create a separate duplicate weight
      tracker entry unless the user explicitly asks.
      For updating or deleting entries, if the user gives a concrete entry ID and the intended
      change/delete action, call update_tracker_entry or delete_tracker_entry immediately so the
      backend can issue its confirmation token. If the target is unclear, query first or ask.
      Do not include internal entry IDs in normal confirmations or summaries. Only include IDs
      when the user explicitly asks to edit, delete, audit, or show record IDs.
      """;

  static final String AGENT_SYSTEM_PROMPT = SYSTEM_PROMPT + """

      CRITICAL -- You MUST use tools for these actions. Never just talk about doing them:
      - When the user asks to record, log, save, or track something -> call create_tracker_entry
        (for health metrics) or create_food_entry (for meals/food). Do NOT say "I've noted that"
        without actually calling the tool.
      - When the user asks about their data, history, stats, or trends -> call query_tracker_data.
        Do NOT rely only on context; always get fresh data.
      - When the user asks what their profile says now, including height, current weight,
        target weight, activity level, or daily routine -> call query_profile. Do NOT answer
        from recent conversation memory, because the user may have changed profile settings
        outside this chat.
      - When the user asks to update profile facts such as height, current weight, target weight,
        goal duration, activity level, or daily routine -> call update_profile. Do NOT create a
        tracker entry for height, and do NOT say profile updates are unavailable. For current
        weight, update_profile writes the latest WEIGHT tracker entry and leaves startingWeight
        unchanged.
      - When the user asks to change or fix an entry and provides a concrete entry ID plus the
        new value/date/notes -> call update_tracker_entry immediately. The backend will stop
        execution and return a confirmation token before any data changes.
      - When the user asks to change or fix an entry but does not provide a concrete entry ID
        or target value -> query first to find candidate entries, then ask the user to choose.
      - When the user asks to remove or delete an entry and provides a concrete entry ID -> call
        delete_tracker_entry immediately. The backend will stop execution and return a
        confirmation token before any data changes.
      - When the user asks to remove or delete something vague -> query first and ask the user
        to choose the exact entry ID. Do not guess.
      - The backend enforces confirmation for update_tracker_entry and delete_tracker_entry.
        If a tool result says "Confirmation required" with a token, tell the user no data has
        changed yet and ask them to reply exactly with that token. After the user confirms, call
        the same tool again with the same arguments plus confirmation_token. Never invent or
        alter the token.
      - If the user's request is ambiguous (e.g. "change my sleep"), ask which specific
        entry or what the new value should be. Do not guess.
      - When you receive tool results, use them to formulate your answer. Do not ask the
        user to look up data you just received.
      - If a tool returns an error, explain the problem to the user and suggest a fix.
      - For food logging confirmations, answer with a compact Markdown table containing
        food, meal, and calories only. Do not show internal entry IDs.

      FOOD TRACKER -- use create_food_entry once per meal:
      - create_food_entry needs meal_type (BREAKFAST/LUNCH/DINNER/SNACK) and items[].
      - Each items[] element needs food_name and calories. grams, protein_grams, carbs_grams,
        fat_grams are OPTIONAL -- omit if unknown.
      - If the user logs a meal with multiple foods, call create_food_entry ONCE with multiple
        items. Do not make one tool call per food.
      - Estimate calories from common portions: bowl of rice ~ 260kcal, chicken breast 150g ~ 250kcal,
        apple ~ 95kcal, egg ~ 70kcal.
      - To edit/delete food records: query data and use the tracker entry_id shown in tool results,
        but show that ID to the user only when they are choosing a record to edit or delete.
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
