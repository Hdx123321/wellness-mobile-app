package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.tracker.api.TrackerEntryRequest;
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
 * Create a new tracker entry on behalf of the user.
 * This is a safe (non-destructive) operation — executed directly.
 */
@Component
class CreateTrackerEntryTool implements Tool {

  private final TrackerService tracker;

  CreateTrackerEntryTool(TrackerService tracker) {
    this.tracker = tracker;
  }

  @Override
  public String name() { return "create_tracker_entry"; }

  @Override
  public String description() {
    return "Create a new tracker entry. Use this when the user wants to record or log a value. "
        + "Always confirm the type, amount, and date before calling.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> trackerType = new LinkedHashMap<>();
    trackerType.put("type", "string");
    trackerType.put("description", "The tracker type");
    trackerType.put("enum", List.of(
        "WEIGHT", "SLEEP", "STEPS", "WORKOUT", "WATER",
        "MEDICINE", "HEART_RATE", "BLOOD_GLUCOSE"));

    Map<String, Object> amount = new LinkedHashMap<>();
    amount.put("type", "number");
    amount.put("description", "Numeric value in the tracker's unit (e.g. kg for WEIGHT, min for SLEEP)");

    Map<String, Object> date = new LinkedHashMap<>();
    date.put("type", "string");
    date.put("description", "ISO date (YYYY-MM-DD). Default is today.");

    Map<String, Object> notes = new LinkedHashMap<>();
    notes.put("type", "string");
    notes.put("description", "Optional notes about this entry");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("tracker_type", trackerType);
    props.put("amount", amount);
    props.put("recorded_date", date);
    props.put("notes", notes);

    schema.put("properties", props);
    schema.put("required", List.of("tracker_type", "amount"));
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    String typeStr = args.path("tracker_type").asText();
    TrackerType type;
    try {
      type = TrackerType.valueOf(typeStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return "Error: unknown tracker type '" + typeStr + "'";
    }

    BigDecimal amount = new BigDecimal(args.path("amount").asText())
        .setScale(2, RoundingMode.HALF_UP);

    Instant recordedAt = parseDate(args);
    String notes = args.has("notes") && !args.path("notes").asText().isBlank()
        ? args.path("notes").asText().trim() : null;

    try {
      var result = tracker.create(userId,
          new TrackerEntryRequest(type, recordedAt, amount, null, notes));
      return "Created entry #" + result.id() + ": " + type.name()
          + " " + amount + type.unit() + " on " + recordedAt;
    } catch (Exception e) {
      return "Error creating " + typeStr + " entry: " + e.getMessage();
    }
  }

  private Instant parseDate(JsonNode args) {
    String dateStr = args.has("recorded_date") ? args.path("recorded_date").asText() : null;
    if (dateStr == null || dateStr.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.parse(dateStr);
    } catch (DateTimeParseException e1) {
      try {
        // Try appending time component
        return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e2) {
        return Instant.now();
      }
    }
  }
}
