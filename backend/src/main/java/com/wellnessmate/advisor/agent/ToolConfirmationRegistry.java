package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory confirmation gate for destructive agent tools.
 *
 * <p>The first destructive call creates a short-lived confirmation token and does not execute.
 * A later call must provide the same tool arguments plus that token to execute.</p>
 */
@Component
public class ToolConfirmationRegistry {

  private static final Duration TTL = Duration.ofMinutes(10);

  private final ObjectMapper mapper;
  private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();

  public ToolConfirmationRegistry(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ConfirmationDecision check(Long userId, String toolName, JsonNode args) {
    String supplied = args.path("confirmation_token").asText("");
    String fingerprint = fingerprint(toolName, args);

    if (!supplied.isBlank()) {
      PendingAction action = pending.remove(supplied);
      if (action == null || action.expiresAt().isBefore(Instant.now())) {
        return ConfirmationDecision.rejectedDecision("Confirmation token is missing or expired. Ask the user to confirm again.");
      }
      if (!action.userId().equals(userId) || !action.toolName().equals(toolName)
          || !action.fingerprint().equals(fingerprint)) {
        return ConfirmationDecision.rejectedDecision("Confirmation token does not match this exact action. Ask the user to confirm again.");
      }
      return ConfirmationDecision.approvedDecision();
    }

    String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    pending.put(token, new PendingAction(userId, toolName, fingerprint, Instant.now().plus(TTL)));
    return ConfirmationDecision.requiredDecision(token);
  }

  private String fingerprint(String toolName, JsonNode args) {
    try {
      ObjectNode copy = args.deepCopy();
      copy.remove("confirmation_token");
      return toolName + ":" + mapper.writeValueAsString(sortObject(copy));
    } catch (Exception e) {
      return toolName + ":" + args.toString();
    }
  }

  private JsonNode sortObject(JsonNode node) {
    if (!node.isObject()) return node;
    ObjectNode sorted = mapper.createObjectNode();
    Iterator<String> names = node.fieldNames();
    java.util.List<String> fields = new java.util.ArrayList<>();
    names.forEachRemaining(fields::add);
    fields.stream().sorted().forEach(name -> sorted.set(name, sortObject(node.get(name))));
    return sorted;
  }

  private record PendingAction(Long userId, String toolName, String fingerprint, Instant expiresAt) {}

  public record ConfirmationDecision(boolean approved, boolean required, String token, String message) {
    static ConfirmationDecision approvedDecision() {
      return new ConfirmationDecision(true, false, null, null);
    }

    static ConfirmationDecision requiredDecision(String token) {
      return new ConfirmationDecision(false, true, token, null);
    }

    static ConfirmationDecision rejectedDecision(String message) {
      return new ConfirmationDecision(false, false, null, message);
    }
  }
}
