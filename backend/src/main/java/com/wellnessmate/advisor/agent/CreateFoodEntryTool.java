package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellnessmate.food.api.AnalyzedFoodEntryRequest;
import com.wellnessmate.food.api.AnalyzedFoodItemRequest;
import com.wellnessmate.food.domain.MealType;
import com.wellnessmate.food.service.FoodService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Creates one food diary meal with one or more item snapshots.
 */
@Component
class CreateFoodEntryTool implements Tool {
  static final String USER_CONFIRMATION_PREFIX = "USER_FACING_CONFIRMATION:\n";

  private final FoodService foodService;

  CreateFoodEntryTool(FoodService foodService) {
    this.foodService = foodService;
  }

  @Override
  public String name() { return "create_food_entry"; }

  @Override
  public String description() {
    return "Log one meal to the user's food diary. Put every food in that meal into items[]. "
        + "Use one tool call per meal, not one call per food. Only item calories are required; "
        + "estimate grams and macros from typical values if unknown.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> mealType = new LinkedHashMap<>();
    mealType.put("type", "string");
    mealType.put("description", "Meal type for this meal");
    mealType.put("enum", List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"));

    Map<String, Object> foodName = numberOrStringField("string",
        "Food name, e.g. 'White Rice' or 'Grilled Chicken Breast'");
    Map<String, Object> calories = numberOrStringField("number", "Calories in kcal");
    Map<String, Object> grams = numberOrStringField("number", "Weight in grams (optional, default 100)");
    Map<String, Object> protein = numberOrStringField("number", "Protein in grams (optional)");
    Map<String, Object> carbs = numberOrStringField("number", "Carbohydrates in grams (optional)");
    Map<String, Object> fat = numberOrStringField("number", "Fat in grams (optional)");

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("type", "object");
    item.put("properties", Map.of(
        "food_name", foodName,
        "calories", calories,
        "grams", grams,
        "protein_grams", protein,
        "carbs_grams", carbs,
        "fat_grams", fat));
    item.put("required", List.of("food_name", "calories"));

    Map<String, Object> items = new LinkedHashMap<>();
    items.put("type", "array");
    items.put("description", "All foods in the meal. Use one element per food or drink.");
    items.put("minItems", 1);
    items.put("maxItems", 20);
    items.put("items", item);

    Map<String, Object> date = numberOrStringField("string",
        "ISO date YYYY-MM-DD (optional, default today)");
    Map<String, Object> notes = numberOrStringField("string", "Optional meal notes");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("meal_type", mealType);
    props.put("items", items);
    props.put("recorded_date", date);
    props.put("notes", notes);
    // Backward-compatible single-item fields. Prefer items[].
    props.put("food_name", foodName);
    props.put("calories", calories);
    props.put("grams", grams);
    props.put("protein_grams", protein);
    props.put("carbs_grams", carbs);
    props.put("fat_grams", fat);

    schema.put("properties", props);
    schema.put("required", List.of("meal_type", "items"));
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    MealType mealType;
    try {
      mealType = MealType.valueOf(args.path("meal_type").asText().toUpperCase());
    } catch (IllegalArgumentException e) {
      return "Error: unknown meal type '" + args.path("meal_type").asText() + "'";
    }

    List<AnalyzedFoodItemRequest> items = parseItems(args);
    if (items.isEmpty()) {
      return "Error: provide at least one food item with food_name and positive calories";
    }

    Instant recordedAt = parseDate(args);
    String notes = args.has("notes") && !args.path("notes").asText().isBlank()
        ? args.path("notes").asText().trim() : null;

    try {
      var result = foodService.createFromAnalysis(userId,
          new AnalyzedFoodEntryRequest(recordedAt, mealType, items, notes));
      return USER_CONFIRMATION_PREFIX + confirmationMarkdown(result.mealType(), result.items(),
          result.totals().calories());
    } catch (Exception e) {
      return "Error creating food entry: " + e.getMessage();
    }
  }

  private String confirmationMarkdown(String mealType,
                                      List<com.wellnessmate.food.api.FoodEntryItemResponse> items,
                                      BigDecimal totalCalories) {
    StringBuilder sb = new StringBuilder();
    sb.append("Logged your ").append(mealType.toLowerCase()).append(".\n\n")
        .append("| Food | Meal | Calories |\n")
        .append("|---|---|---|\n");
    for (var item : items) {
      sb.append("| ")
          .append(escapeTableCell(item.name()))
          .append(" | ")
          .append(formatMeal(mealType))
          .append(" | ")
          .append(formatNumber(item.nutrients().calories()))
          .append(" kcal |\n");
    }
    sb.append("\n**Total:** ").append(formatNumber(totalCalories)).append(" kcal.");
    return sb.toString();
  }

  private String formatMeal(String mealType) {
    return mealType.charAt(0) + mealType.substring(1).toLowerCase();
  }

  private String formatNumber(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private String escapeTableCell(String value) {
    return value == null ? "" : value.replace("|", "\\|").trim();
  }

  private Map<String, Object> numberOrStringField(String type, String description) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("type", type);
    field.put("description", description);
    return field;
  }

  private List<AnalyzedFoodItemRequest> parseItems(JsonNode args) {
    List<AnalyzedFoodItemRequest> parsed = new ArrayList<>();
    JsonNode itemNodes = args.path("items");
    if (itemNodes.isArray()) {
      itemNodes.forEach(node -> parseItem(node, parsed));
    }
    if (parsed.isEmpty() && args.has("food_name")) {
      parseItem(args, parsed);
    }
    return parsed;
  }

  private void parseItem(JsonNode node, List<AnalyzedFoodItemRequest> parsed) {
    String foodName = node.path("food_name").asText("").trim();
    BigDecimal calories = safeDecimal(node, "calories");
    if (foodName.isBlank() || calories.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }

    BigDecimal grams = safeDecimal(node, "grams");
    if (grams.compareTo(BigDecimal.ZERO) <= 0) grams = new BigDecimal("100");

    parsed.add(new AnalyzedFoodItemRequest(
        foodName,
        grams,
        calories,
        safeDecimal(node, "protein_grams"),
        safeDecimal(node, "carbs_grams"),
        safeDecimal(node, "fat_grams"),
        BigDecimal.ZERO));
  }

  private BigDecimal safeDecimal(JsonNode args, String field) {
    if (!args.has(field) || args.path(field).isNull()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(args.path(field).asText()).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  private Instant parseDate(JsonNode args) {
    String dateStr = args.has("recorded_date") ? args.path("recorded_date").asText() : null;
    if (dateStr == null || dateStr.isBlank()) return Instant.now();
    try {
      return Instant.parse(dateStr);
    } catch (DateTimeParseException e1) {
      try {
        return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e2) {
        return Instant.now();
      }
    }
  }
}
