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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Create a food diary entry via {@link FoodService}.
 * Uses a simple flat schema — one food item per call — so the LLM can reliably invoke it.
 * For multi-item meals the LLM makes multiple calls with the same meal_type.
 */
@Component
class CreateFoodEntryTool implements Tool {

  private final FoodService foodService;

  CreateFoodEntryTool(FoodService foodService) {
    this.foodService = foodService;
  }

  @Override
  public String name() { return "create_food_entry"; }

  @Override
  public String description() {
    return "Log a food item to the user's food diary. "
        + "Call this for EACH food the user mentions (multiple calls for a multi-item meal). "
        + "Only calories is required — estimate grams and macros from typical values if unknown.";
  }

  @Override
  public Map<String, Object> parametersSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> mealType = new LinkedHashMap<>();
    mealType.put("type", "string");
    mealType.put("description", "Meal type for this food item");
    mealType.put("enum", List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"));

    Map<String, Object> foodName = new LinkedHashMap<>();
    foodName.put("type", "string");
    foodName.put("description", "Food name, e.g. 'White Rice' or 'Grilled Chicken Breast'");

    Map<String, Object> calories = new LinkedHashMap<>();
    calories.put("type", "number");
    calories.put("description", "Calories in kcal (required)");

    Map<String, Object> grams = new LinkedHashMap<>();
    grams.put("type", "number");
    grams.put("description", "Weight in grams (optional, default 100)");

    Map<String, Object> protein = new LinkedHashMap<>();
    protein.put("type", "number");
    protein.put("description", "Protein in grams (optional, default 0)");

    Map<String, Object> carbs = new LinkedHashMap<>();
    carbs.put("type", "number");
    carbs.put("description", "Carbohydrates in grams (optional, default 0)");

    Map<String, Object> fat = new LinkedHashMap<>();
    fat.put("type", "number");
    fat.put("description", "Fat in grams (optional, default 0)");

    Map<String, Object> date = new LinkedHashMap<>();
    date.put("type", "string");
    date.put("description", "ISO date YYYY-MM-DD (optional, default today)");

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("meal_type", mealType);
    props.put("food_name", foodName);
    props.put("calories", calories);
    props.put("grams", grams);
    props.put("protein_grams", protein);
    props.put("carbs_grams", carbs);
    props.put("fat_grams", fat);
    props.put("recorded_date", date);

    schema.put("properties", props);
    schema.put("required", List.of("meal_type", "food_name", "calories"));
    return schema;
  }

  @Override
  public String execute(Long userId, JsonNode args) {
    String mealStr = args.path("meal_type").asText();
    MealType mealType;
    try {
      mealType = MealType.valueOf(mealStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return "Error: unknown meal type '" + mealStr + "'";
    }

    String foodName = args.path("food_name").asText().trim();
    if (foodName.isBlank()) {
      return "Error: food_name is required";
    }

    BigDecimal calories = safeDecimal(args, "calories");
    if (calories.compareTo(BigDecimal.ZERO) <= 0) {
      return "Error: calories must be a positive number";
    }

    BigDecimal grams = safeDecimal(args, "grams");
    if (grams.compareTo(BigDecimal.ZERO) <= 0) grams = new BigDecimal("100");

    BigDecimal protein = safeDecimal(args, "protein_grams");
    BigDecimal carbs = safeDecimal(args, "carbs_grams");
    BigDecimal fat = safeDecimal(args, "fat_grams");
    BigDecimal fiber = BigDecimal.ZERO;

    Instant recordedAt = parseDate(args);

    try {
      var item = new AnalyzedFoodItemRequest(foodName, grams, calories,
          protein, carbs, fat, fiber);
      var result = foodService.createFromAnalysis(userId,
          new AnalyzedFoodEntryRequest(recordedAt, mealType, List.of(item), null));
      return "Created food entry #" + result.id() + ": " + foodName
          + " " + result.totals().calories() + "kcal (" + mealType.name() + ")";
    } catch (Exception e) {
      return "Error creating food entry: " + e.getMessage();
    }
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
