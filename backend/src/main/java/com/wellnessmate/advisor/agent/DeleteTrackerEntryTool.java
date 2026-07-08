package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.food.domain.FoodEntry;
import com.wellnessmate.food.repository.FoodEntryRepository;
import com.wellnessmate.food.service.FoodService;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.service.TrackerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Delete an existing tracker entry. This is an irreversible operation —
 * the orchestrator should confirm with the user before executing.
 *
 * <p>For FOOD entries, delegates to {@link FoodService} which cascade-deletes
 * the {@link FoodEntry}, its items, photos, and the linked tracker entry.</p>
 */
@Component
class DeleteTrackerEntryTool implements Tool {

  private final TrackerService tracker;
  private final FoodEntryRepository foodEntryRepo;
  private final FoodService foodService;

  DeleteTrackerEntryTool(TrackerService tracker, FoodEntryRepository foodEntryRepo,
                         FoodService foodService) {
    this.tracker = tracker;
    this.foodEntryRepo = foodEntryRepo;
    this.foodService = foodService;
  }

  @Override
  public String name() { return "delete_tracker_entry"; }

  @Override
  public String description() {
    return "Delete a tracker entry by ID. This is IRREVERSIBLE — only use after querying "
        + "and explicitly confirming with the user which entry to delete. "
        + "Provide the entry ID from a prior query result. "
        + "Works for all tracker types including FOOD (cascade-deletes nutrition details).";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> entryId = new LinkedHashMap<>();
    entryId.put("type", "integer");
    entryId.put("description", "The entry ID to delete (e.g., #42 from query results)");

    Map<String, Object> confirmationToken = new LinkedHashMap<>();
    confirmationToken.put("type", "string");
    confirmationToken.put("description", "Required only after the backend asks for confirmation. Use the exact token the user confirmed.");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("entry_id", entryId);
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

    try {
      var entry = tracker.get(userId, entryId);

      // FOOD entries must cascade through FoodService to clean up
      // FoodEntry + FoodEntryItem + FoodEntryPhoto + TrackerEntry
      if (entry.type() == TrackerType.FOOD) {
        FoodEntry foodEntry = foodEntryRepo.findByTrackerEntryId(entryId)
            .orElse(null);
        if (foodEntry == null) {
          // No FoodEntry linked — fall back to direct tracker delete
          tracker.delete(userId, entryId);
          return "Deleted tracker entry #" + entryId + " (no linked food diary).";
        }
        foodService.delete(userId, foodEntry.getId());
        return "Deleted food entry #" + foodEntry.getId()
            + " (tracker #" + entryId + ") — including nutrition details and photos.";
      }

      tracker.delete(userId, entryId);
      return "Deleted entry #" + entryId + " successfully.";
    } catch (Exception e) {
      return "Error deleting entry #" + entryId + ": " + e.getMessage();
    }
  }
}
