package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.advisor.service.RagRetrievalService;
import com.wellnessmate.tracker.domain.TrackerType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Query tracker data for a single type over a configurable look-back window.
 * Returns aggregated stats, recent individual entries (with IDs), and anomalies.
 */
@Component
class QueryTrackerDataTool implements Tool {

  private final RagRetrievalService rag;

  QueryTrackerDataTool(RagRetrievalService rag) {
    this.rag = rag;
  }

  @Override
  public String name() { return "query_tracker_data"; }

  @Override
  public String description() {
    return "Query the user's tracker data by type and look-back period. "
        + "Returns stats (avg/min/max/trend), individual entries with IDs, and anomalies. "
        + "Use this before updating or deleting entries so you know the entry IDs.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> trackerType = new LinkedHashMap<>();
    trackerType.put("type", "string");
    trackerType.put("description", "The tracker type to query");
    trackerType.put("enum", List.of(
        "WEIGHT", "SLEEP", "STEPS", "WORKOUT", "WATER",
        "MEDICINE", "HEART_RATE", "BLOOD_GLUCOSE", "FOOD"));

    Map<String, Object> days = new LinkedHashMap<>();
    days.put("type", "integer");
    days.put("description", "Number of days to look back. Default 7. Use larger values only when the user asks for a longer period.");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("tracker_type", trackerType);
    props.put("days", days);

    schema.put("properties", props);
    schema.put("required", List.of("tracker_type"));
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    String typeStr = args.path("tracker_type").asText();
    if (typeStr == null || typeStr.isBlank()) {
      return "Error: tracker_type is required";
    }
    TrackerType type;
    try {
      type = TrackerType.valueOf(typeStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return "Error: unknown tracker type '" + typeStr + "'. Valid types: WEIGHT, SLEEP, STEPS, WORKOUT, WATER, MEDICINE, HEART_RATE, BLOOD_GLUCOSE";
    }
    int days = args.has("days") ? args.path("days").asInt(7) : 7;
    if (days < 1) days = 7;
    if (days > 365) days = 365;

    try {
      return rag.queryTrackerType(userId, type, days);
    } catch (Exception e) {
      return "Error querying " + typeStr + " data: " + e.getMessage();
    }
  }
}
