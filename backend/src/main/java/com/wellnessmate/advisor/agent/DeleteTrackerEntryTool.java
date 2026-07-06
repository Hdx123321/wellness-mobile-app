package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.tracker.service.TrackerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Delete an existing tracker entry. This is an irreversible operation —
 * the orchestrator should confirm with the user before executing.
 */
@Component
class DeleteTrackerEntryTool implements Tool {

  private final TrackerService tracker;

  DeleteTrackerEntryTool(TrackerService tracker) {
    this.tracker = tracker;
  }

  @Override
  public String name() { return "delete_tracker_entry"; }

  @Override
  public String description() {
    return "Delete a tracker entry by ID. This is IRREVERSIBLE — only use after querying "
        + "and explicitly confirming with the user which entry to delete. "
        + "Provide the entry ID from a prior query result.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> entryId = new LinkedHashMap<>();
    entryId.put("type", "integer");
    entryId.put("description", "The entry ID to delete (e.g., #42 from query results)");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("entry_id", entryId);

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

    try {
      tracker.delete(userId, entryId);
      return "Deleted entry #" + entryId + " successfully.";
    } catch (Exception e) {
      return "Error deleting entry #" + entryId + ": " + e.getMessage();
    }
  }
}
