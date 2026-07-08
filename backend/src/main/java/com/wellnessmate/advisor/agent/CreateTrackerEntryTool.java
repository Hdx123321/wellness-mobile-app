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
  static final String USER_CONFIRMATION_PREFIX = "USER_FACING_CONFIRMATION:\n";

  private final TrackerService tracker;

  CreateTrackerEntryTool(TrackerService tracker) {
    this.tracker = tracker;
  }

  @Override
  public String name() { return "create_tracker_entry"; }

  @Override
  public String description() {
    return "Create a new health tracker entry. Use this for health metrics like weight, sleep, "
        + "steps, workout, water, and medicine. "
        + "For food/meal logging, use create_food_entry instead (this tool cannot create food entries). "
        + "Always confirm the type, amount, and date before calling.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> trackerType = new LinkedHashMap<>();
    trackerType.put("type", "string");
    trackerType.put("description", "The tracker type (NOT for food/meals — use create_food_entry for that)");
    trackerType.put("enum", List.of(
        "WEIGHT", "SLEEP", "STEPS", "WORKOUT", "WATER", "MEDICINE"));

    Map<String, Object> amount = new LinkedHashMap<>();
    amount.put("type", "number");
    amount.put("description", "Numeric value in the tracker's unit (e.g. kg for WEIGHT, min for SLEEP)");

    Map<String, Object> date = new LinkedHashMap<>();
    date.put("type", "string");
    date.put("description", "ISO date (YYYY-MM-DD). Default is today.");

    Map<String, Object> notes = new LinkedHashMap<>();
    notes.put("type", "string");
    notes.put("description", "Optional notes about this entry");

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("type", "string");
    detail.put("description", "Required detail for WORKOUT (workout type, e.g. Swimming) and MEDICINE (medicine name). Optional for other tracker types.");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("tracker_type", trackerType);
    props.put("amount", amount);
    props.put("recorded_date", date);
    props.put("detail", detail);
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

    BigDecimal amount;
    try {
      amount = new BigDecimal(args.path("amount").asText())
          .setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return "Error: amount must be a numeric value in the tracker unit for " + type.name();
    }
    String validationError = validateAmount(type, amount);
    if (validationError != null) {
      return validationError;
    }

    Instant recordedAt = parseDate(args);
    String notes = args.has("notes") && !args.path("notes").asText().isBlank()
        ? args.path("notes").asText().trim() : null;
    String detail = args.has("detail") && !args.path("detail").asText().isBlank()
        ? args.path("detail").asText().trim() : null;
    if (detail == null && type.detailRequired()) {
      detail = notes;
    }
    if (detail == null && type.detailRequired()) {
      return "Error: detail is required for " + type.name()
          + (type == TrackerType.WORKOUT ? " (workout type)" : " (medicine name)");
    }

    try {
      var result = tracker.create(userId,
          new TrackerEntryRequest(type, recordedAt, amount, detail, notes));
      return USER_CONFIRMATION_PREFIX + confirmationMarkdown(type, result.amount(), result.unit(),
          result.detail(), result.recordedAt());
    } catch (Exception e) {
      return "Error creating " + typeStr + " entry: " + e.getMessage();
    }
  }

  private String confirmationMarkdown(TrackerType type, BigDecimal amount, String unit,
                                      String detail, Instant recordedAt) {
    String label = switch (type) {
      case WEIGHT -> "weight";
      case SLEEP -> "sleep";
      case STEPS -> "steps";
      case WORKOUT -> "workout";
      case WATER -> "water";
      case MEDICINE -> "medicine";
      case FOOD -> "food";
    };
    StringBuilder sb = new StringBuilder();
    sb.append("Logged your ").append(label).append(".\n\n")
        .append("| Type | Detail | Amount | Date |\n")
        .append("|---|---|---|---|\n")
        .append("| ")
        .append(type.name())
        .append(" | ")
        .append(detail == null || detail.isBlank() ? "-" : detail.replace("|", "\\|"))
        .append(" | ")
        .append(amount.stripTrailingZeros().toPlainString())
        .append(" ")
        .append(unit)
        .append(" | ")
        .append(recordedAt.atZone(ZoneOffset.UTC).toLocalDate())
        .append(" |");
    return sb.toString();
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

  private String validateAmount(TrackerType type, BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      return "Error: amount must be greater than zero for " + type.name();
    }
    BigDecimal max = switch (type) {
      case WEIGHT -> new BigDecimal("400");
      case SLEEP -> new BigDecimal("1440");
      case STEPS -> new BigDecimal("100000");
      case WORKOUT -> new BigDecimal("1440");
      case WATER -> new BigDecimal("20000");
      case MEDICINE -> new BigDecimal("100");
      case FOOD -> BigDecimal.ZERO;
    };
    if (max.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(max) > 0) {
      return "Error: amount is implausibly high for " + type.name() + "; please confirm the unit/value.";
    }
    return null;
  }
}
