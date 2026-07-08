package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.tracker.api.TrackerEntryRequest;
import com.wellnessmate.tracker.api.TrackerEntryResponse;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.service.TrackerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Update an existing tracker entry. This is a dangerous operation —
 * the orchestrator should confirm with the user before executing.
 */
@Component
class UpdateTrackerEntryTool implements Tool {

  private final TrackerService tracker;

  UpdateTrackerEntryTool(TrackerService tracker) {
    this.tracker = tracker;
  }

  @Override
  public String name() { return "update_tracker_entry"; }

  @Override
  public String description() {
    return "Update an existing tracker entry by ID. Only use this after querying the data "
        + "and confirming with the user which entry to change and what to change it to. "
        + "Provide the entry ID from a prior query result.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> entryId = new LinkedHashMap<>();
    entryId.put("type", "integer");
    entryId.put("description", "The entry ID (e.g. #42 from query results)");

    Map<String, Object> amount = new LinkedHashMap<>();
    amount.put("type", "number");
    amount.put("description", "New amount value. Omit to keep current.");

    Map<String, Object> date = new LinkedHashMap<>();
    date.put("type", "string");
    date.put("description", "New date (YYYY-MM-DD). Omit to keep current.");

    Map<String, Object> notes = new LinkedHashMap<>();
    notes.put("type", "string");
    notes.put("description", "New notes. Omit to keep current. Pass empty string to clear notes.");

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("type", "string");
    detail.put("description", "New detail. Required for WORKOUT (workout type) and MEDICINE (medicine name). Omit to keep current. Pass empty string to clear detail only for tracker types that do not require detail.");

    Map<String, Object> confirmationToken = new LinkedHashMap<>();
    confirmationToken.put("type", "string");
    confirmationToken.put("description", "Required only after the backend asks for confirmation. Use the exact token the user confirmed.");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("entry_id", entryId);
    props.put("amount", amount);
    props.put("recorded_date", date);
    props.put("detail", detail);
    props.put("notes", notes);
    props.put("confirmation_token", confirmationToken);

    schema.put("properties", props);
    schema.put("required", List.of("entry_id"));
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    long entryId = args.path("entry_id").asLong(0);
    if (entryId <= 0) {
      return "Error: entry_id is required";
    }

    // Fetch current state
    TrackerEntryResponse current;
    try {
      current = tracker.get(userId, entryId);
    } catch (Exception e) {
      return "Error: entry #" + entryId + " not found or not accessible. " + e.getMessage();
    }

    // Food entries have item-level nutrition data — updating them requires
    // re-creating the full entry. Guide the LLM to use delete + create_food_entry.
    if (current.type() == TrackerType.FOOD) {
      return "Error: Food entries cannot be updated in-place because they contain "
          + "item-level nutrition details. To modify a food entry, confirm with the user, "
          + "then call delete_tracker_entry to remove the old entry and create_food_entry "
          + "to create a new one with the correct values.";
    }

    BigDecimal amount;
    try {
      amount = args.has("amount") && !args.path("amount").isNull()
          ? new BigDecimal(args.path("amount").asText()).setScale(2, RoundingMode.HALF_UP)
          : current.amount();
    } catch (NumberFormatException e) {
      return "Error: amount must be a numeric value in the tracker unit for " + current.type().name();
    }

    Instant recordedAt = parseDate(args, current);

    String notes;
    if (args.has("notes")) {
      JsonNode n = args.path("notes");
      notes = n.isNull() || n.asText().isEmpty() ? null : n.asText().trim();
    } else {
      notes = current.notes();
    }

    String detail;
    if (args.has("detail")) {
      JsonNode d = args.path("detail");
      detail = d.isNull() || d.asText().isEmpty() ? null : d.asText().trim();
    } else {
      detail = current.detail();
    }

    try {
      var result = tracker.update(userId, entryId,
          new TrackerEntryRequest(current.type(), recordedAt, amount, detail, notes));
      return "Updated entry #" + result.id() + ": " + result.type().name()
          + " " + result.amount() + result.type().unit()
          + " on " + result.recordedAt();
    } catch (Exception e) {
      return "Error updating entry #" + entryId + ": " + e.getMessage();
    }
  }

  String preflight(Long userId, JsonNode args) {
    long entryId = args.path("entry_id").asLong(0);
    if (entryId <= 0) {
      return "Error: entry_id is required";
    }
    TrackerEntryResponse current;
    try {
      current = tracker.get(userId, entryId);
    } catch (Exception e) {
      return "Error: entry #" + entryId + " not found or not accessible. " + e.getMessage();
    }
    if (current.type() == TrackerType.FOOD) {
      return "Error: Food entries cannot be updated in-place because they contain "
          + "item-level nutrition details. To modify a food entry, delete the old entry "
          + "and create a new food entry with the corrected items after the user confirms.";
    }
    return null;
  }

  private Instant parseDate(JsonNode args, TrackerEntryResponse current) {
    String dateStr = args.has("recorded_date") ? args.path("recorded_date").asText() : null;
    if (dateStr == null || dateStr.isBlank()) {
      return current.recordedAt();
    }
    try {
      return Instant.parse(dateStr);
    } catch (DateTimeParseException e1) {
      try {
        return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e2) {
        return current.recordedAt();
      }
    }
  }
}
