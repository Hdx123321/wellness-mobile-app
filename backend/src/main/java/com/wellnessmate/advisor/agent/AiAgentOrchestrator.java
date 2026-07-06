package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.service.AiAdvisorClient;
import com.wellnessmate.common.api.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Orchestrates multi-round agent tool-calling loops.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Send messages + tools to the LLM via {@link AiAdvisorClient#chatWithTools}.</li>
 *   <li>Stream text tokens to the SSE emitter.</li>
 *   <li>If the model returns tool calls: emit {@code tool_call} SSE events, execute
 *       each tool, emit {@code tool_result} events, append results to messages, loop.</li>
 *   <li>If the model returns a text answer: emit the final {@code done} event and call
 *       {@code onAnswer} with the full text for persistence.</li>
 *   <li>Hard cap at 5 rounds to prevent infinite loops.</li>
 * </ol>
 */
@Component
public class AiAgentOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(AiAgentOrchestrator.class);
  private static final int MAX_ROUNDS = 5;

  private final AiAdvisorClient client;
  private final ToolRegistry registry;
  private final ObjectMapper mapper;

  public AiAgentOrchestrator(AiAdvisorClient client, ToolRegistry registry, ObjectMapper mapper) {
    this.client = client;
    this.registry = registry;
    this.mapper = mapper;
  }

  /**
   * Run the agent loop.
   *
   * @param userId      the authenticated user
   * @param messages    initial message list (will be mutated by appending tool results)
   * @param emitter     SSE emitter for streaming tokens and status events to the client
   * @param onAnswer    called with the final answer text for persistence
   * @param onError     called on unrecoverable failure
   */
  public void run(Long userId, List<Map<String, Object>> messages,
                  SseEmitter emitter,
                  Consumer<String> onAnswer,
                  Consumer<Throwable> onError) {
    var tools = registry.toOpenAiTools();

    // Use a thread-safe list so we can append tool results across rounds
    var msgList = new ArrayList<>(messages);

    for (int round = 0; round < MAX_ROUNDS; round++) {
      StringBuilder fullAnswer = new StringBuilder();
      RoundResult result = new RoundResult();
      var thinkingActive = new boolean[]{false};

      client.chatWithTools(msgList, tools,
          // onThinkingToken — reasoning/chain-of-thought
          thinkingToken -> {
            if (!thinkingActive[0]) {
              safeEmit(emitter, Map.of("type", "thinking_start"));
              thinkingActive[0] = true;
            }
            safeEmit(emitter, thinkingToken);
          },
          // onToken — actual reply content
          token -> {
            if (thinkingActive[0]) {
              safeEmit(emitter, Map.of("type", "thinking_end"));
              thinkingActive[0] = false;
            }
            fullAnswer.append(token);
            safeEmit(emitter, token);
          },
          // onToolCalls
          toolCalls -> {
            if (thinkingActive[0]) {
              safeEmit(emitter, Map.of("type", "thinking_end"));
              thinkingActive[0] = false;
            }
            result.toolCalls = true;
            result.calls = toolCalls;
            result.latch.countDown();
          },
          // onError
          error -> {
            result.error = error;
            result.latch.countDown();
          },
          // onComplete
          () -> {
            if (thinkingActive[0]) {
              safeEmit(emitter, Map.of("type", "thinking_end"));
              thinkingActive[0] = false;
            }
            result.complete = true;
            result.fullAnswer = fullAnswer.toString();
            result.latch.countDown();
          });

      try {
        // Wait for this round to finish (120s timeout)
        boolean done = result.latch.await(120, TimeUnit.SECONDS);
        if (!done) {
          onError.accept(new ApiException(HttpStatus.BAD_GATEWAY,
              "AI_ADVISOR_TIMEOUT", "AI advisor request timed out"));
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        onError.accept(e);
        return;
      }

      // Handle error
      if (result.error != null) {
        onError.accept(result.error);
        return;
      }

      // Handle tool calls — execute and loop
      if (result.toolCalls) {
        List<Map<String, Object>> toolResults = executeTools(userId, result.calls, emitter);

        // Append assistant's tool_call message and tool result messages to history
        msgList.add(buildAssistantToolCallMessage(result.calls));
        msgList.addAll(toolResults);

        continue; // next round
      }

      // Handle normal completion — text answer received
      if (result.complete) {
        onAnswer.accept(result.fullAnswer);
        return;
      }

      // Shouldn't reach here
      log.warn("Round {} ended with no completion signal", round);
      break;
    }

    // Exhausted max rounds
    safeEmit(emitter, Map.of("type", "error", "message",
        "I ran into a loop trying to answer your question. Please try rephrasing."));
    emitter.complete();
  }

  // ── Tool execution ──

  /**
   * Execute each tool call sequentially, emitting SSE events for the client.
   * System errors (e.g. DB down) are surfaced as {@code error} events and stop the loop.
   * Business errors (e.g. validation) are returned as tool result strings for the LLM to handle.
   */
  private List<Map<String, Object>> executeTools(Long userId, List<ToolCall> calls,
                                                  SseEmitter emitter) {
    List<Map<String, Object>> results = new ArrayList<>();

    for (ToolCall call : calls) {
      // Notify client
      safeEmit(emitter, Map.of(
          "type", "tool_call",
          "name", call.name(),
          "args", (Object) call.arguments()));

      String resultText;
      boolean success;
      try {
        Tool tool = registry.get(call.name());
        if (tool == null) {
          resultText = "Error: unknown tool '" + call.name() + "'";
          success = false;
        } else {
          JsonNode args = mapper.readTree(call.arguments());
          resultText = tool.execute(userId, args);
          success = !resultText.startsWith("Error:");
        }
      } catch (JsonProcessingException e) {
        resultText = "Error parsing tool arguments: " + e.getMessage();
        success = false;
      } catch (Exception e) {
        log.error("Tool execution failed: {} — {}", call.name(), e.getMessage());
        // System-level error — surface to client
        safeEmit(emitter, Map.of("type", "error", "message",
            "Something went wrong while processing your request. Please try again."));
        emitter.complete();
        return results; // short-circuit — don't continue the loop
      }

      // Notify client of result
      safeEmit(emitter, Map.of(
          "type", "tool_result",
          "name", call.name(),
          "success", success));

      // Append tool result as a message for the LLM
      results.add(Map.of("role", "tool",
          "tool_call_id", (Object) call.id(),
          "content", (Object) resultText));
    }

    return results;
  }

  // ── Message builder helpers ──

  private Map<String, Object> buildAssistantToolCallMessage(List<ToolCall> calls) {
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("role", "assistant");
    List<Map<String, Object>> tcList = new ArrayList<>();
    for (ToolCall call : calls) {
      Map<String, Object> tc = new LinkedHashMap<>();
      tc.put("id", call.id());
      tc.put("type", "function");
      Map<String, Object> fn = new LinkedHashMap<>();
      fn.put("name", call.name());
      fn.put("arguments", call.arguments());
      tc.put("function", fn);
      tcList.add(tc);
    }
    msg.put("tool_calls", tcList);
    return msg;
  }

  // ── SSE helpers ──

  private void safeEmit(SseEmitter emitter, String token) {
    try {
      emitter.send(SseEmitter.event().data(token));
    } catch (Exception e) {
      log.debug("SSE send failed (client likely disconnected)");
    }
  }

  private void safeEmit(SseEmitter emitter, Map<String, Object> event) {
    try {
      emitter.send(SseEmitter.event().data(mapper.writeValueAsString(event)));
    } catch (Exception e) {
      log.debug("SSE send failed (client likely disconnected)");
    }
  }

  // ── Internal state holder ──

  private static class RoundResult {
    CountDownLatch latch = new CountDownLatch(1);
    boolean complete;
    boolean toolCalls;
    List<ToolCall> calls;
    Throwable error;
    String fullAnswer;
  }
}
