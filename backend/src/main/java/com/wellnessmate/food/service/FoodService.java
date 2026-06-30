package com.wellnessmate.food.service;

import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.food.api.AnalyzedFoodEntryRequest;
import com.wellnessmate.food.api.AnalyzedFoodItemRequest;
import com.wellnessmate.food.api.CatalogFoodItemRequest;
import com.wellnessmate.food.api.FoodCatalogResponse;
import com.wellnessmate.food.api.FoodEntryRequest;
import com.wellnessmate.food.api.FoodEntryResponse;
import com.wellnessmate.food.api.FoodNutrients;
import com.wellnessmate.food.domain.FoodCatalogItem;
import com.wellnessmate.food.domain.FoodEntry;
import com.wellnessmate.food.domain.FoodEntryItem;
import com.wellnessmate.food.domain.FoodEntryPhoto;
import com.wellnessmate.food.domain.FoodEntrySource;
import com.wellnessmate.food.domain.MealType;
import com.wellnessmate.food.repository.FoodCatalogRepository;
import com.wellnessmate.food.repository.FoodEntryItemRepository;
import com.wellnessmate.food.repository.FoodEntryPhotoRepository;
import com.wellnessmate.food.repository.FoodEntryRepository;
import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerSource;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Food catalog lookup and server-side nutrient calculation. */
@Service
public class FoodService {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal MAX_MEAL_CALORIES = new BigDecimal("20000");

  private final FoodCatalogRepository catalog;
  private final FoodEntryRepository foodEntries;
  private final FoodEntryItemRepository foodItems;
  private final FoodEntryPhotoRepository foodPhotos;
  private final TrackerEntryRepository trackerEntries;

  public FoodService(FoodCatalogRepository catalog, FoodEntryRepository foodEntries,
                     FoodEntryItemRepository foodItems, FoodEntryPhotoRepository foodPhotos,
                     TrackerEntryRepository trackerEntries) {
    this.catalog = catalog;
    this.foodEntries = foodEntries;
    this.foodItems = foodItems;
    this.foodPhotos = foodPhotos;
    this.trackerEntries = trackerEntries;
  }

  @Transactional(readOnly = true)
  public List<FoodCatalogResponse> search(String query, int limit) {
    String normalized = query == null ? "" : query.trim();
    return catalog.search(normalized, PageRequest.of(0, Math.max(1, Math.min(limit, 50))))
        .stream().map(FoodCatalogResponse::from).toList();
  }

  @Transactional
  public FoodEntryResponse createFromCatalog(Long userId, FoodEntryRequest request) {
    validateTime(request.recordedAt());
    Map<Long, FoodCatalogItem> foods = catalog.findAllById(
            request.items().stream().map(CatalogFoodItemRequest::foodId).distinct().toList())
        .stream().collect(Collectors.toMap(FoodCatalogItem::getId, Function.identity()));
    if (foods.size() != request.items().stream().map(CatalogFoodItemRequest::foodId).distinct().count()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FOOD_NOT_FOUND", "One or more foods were not found");
    }
    List<ItemSnapshot> snapshots = request.items().stream().map(item -> {
      FoodCatalogItem food = foods.get(item.foodId());
      BigDecimal factor = item.grams().divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
      return new ItemSnapshot(food.getId(), food.getName(), scaled(item.grams()),
          nutrients(food.getCaloriesPer100g().multiply(factor),
              food.getProteinPer100g().multiply(factor),
              food.getCarbohydratePer100g().multiply(factor),
              food.getFatPer100g().multiply(factor), food.getFiberPer100g().multiply(factor)));
    }).toList();
    return persist(userId, request.recordedAt(), request.mealType(), FoodEntrySource.MANUAL,
        normalized(request.notes()), snapshots);
  }

  @Transactional
  public FoodEntryResponse createFromAnalysis(Long userId, AnalyzedFoodEntryRequest request) {
    validateTime(request.recordedAt());
    List<ItemSnapshot> snapshots = request.items().stream().map(this::snapshot).toList();
    return persist(userId, request.recordedAt(), request.mealType(), FoodEntrySource.AI,
        normalized(request.notes()), snapshots);
  }

  @Transactional
  public FoodEntryResponse createFromAnalysisPhoto(Long userId, AnalyzedFoodEntryRequest request,
                                                   String contentType, byte[] thumbnail) {
    validateThumbnail(contentType, thumbnail);
    FoodEntryResponse response = createFromAnalysis(userId, request);
    foodPhotos.save(new FoodEntryPhoto(response.id(), normalizedContentType(contentType), thumbnail));
    return new FoodEntryResponse(response.id(), response.trackerEntryId(), response.recordedAt(),
        response.mealType(), response.source(), response.notes(), response.items(), response.totals(), true);
  }

  @Transactional(readOnly = true)
  public List<FoodEntryResponse> list(Long userId, Instant from, Instant to) {
    if (from == null || to == null || !from.isBefore(to)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from and to are required and from must be before to");
    }
    List<FoodEntry> entries = foodEntries
        .findByUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(userId, from, to);
    Map<Long, List<FoodEntryItem>> grouped = entries.isEmpty() ? Map.of() : foodItems
        .findByFoodEntryIdInOrderById(entries.stream().map(FoodEntry::getId).toList())
        .stream().collect(Collectors.groupingBy(FoodEntryItem::getFoodEntryId));
    Map<Long, Boolean> photoAvailable = entries.isEmpty() ? Map.of() : foodPhotos
        .findByFoodEntryIdIn(entries.stream().map(FoodEntry::getId).toList())
        .stream().collect(Collectors.toMap(FoodEntryPhoto::getFoodEntryId, photo -> true));
    return entries.stream().map(entry -> {
      List<FoodEntryItem> items = grouped.getOrDefault(entry.getId(), List.of());
      return FoodEntryResponse.from(entry, items, total(items),
          photoAvailable.getOrDefault(entry.getId(), false));
    }).toList();
  }

  @Transactional(readOnly = true)
  public FoodEntryPhoto thumbnail(Long userId, Long foodEntryId) {
    FoodEntry entry = foodEntries.findByIdAndUserId(foodEntryId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FOOD_ENTRY_NOT_FOUND", "Food entry not found"));
    return foodPhotos.findByFoodEntryId(entry.getId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FOOD_THUMBNAIL_NOT_FOUND", "Food thumbnail not found"));
  }

  @Transactional
  public void delete(Long userId, Long id) {
    FoodEntry entry = foodEntries.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FOOD_ENTRY_NOT_FOUND", "Food entry not found"));
    foodPhotos.deleteByFoodEntryId(entry.getId());
    trackerEntries.deleteById(entry.getTrackerEntryId());
  }

  private FoodEntryResponse persist(Long userId, Instant recordedAt,
                                    MealType mealType, FoodEntrySource source,
                                    String notes, List<ItemSnapshot> snapshots) {
    FoodNutrients totals = totalSnapshots(snapshots);
    if (totals.calories().compareTo(MAX_MEAL_CALORIES) > 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FOOD_CALORIES_OUT_OF_RANGE", "Meal calories are out of range");
    }
    String detail = snapshots.stream().map(ItemSnapshot::name).limit(3).collect(Collectors.joining(", "));
    if (snapshots.size() > 3) detail += " +" + (snapshots.size() - 3);
    TrackerSource trackerSource = source == FoodEntrySource.AI ? TrackerSource.AI : TrackerSource.MANUAL;
    TrackerEntry tracker = trackerEntries.save(new TrackerEntry(userId, TrackerType.FOOD, recordedAt,
        totals.calories(), detail, notes, trackerSource));
    FoodEntry entry = foodEntries.save(new FoodEntry(
        userId, tracker.getId(), recordedAt, source, mealType, notes));
    List<FoodEntryItem> saved = foodItems.saveAll(snapshots.stream().map(item -> new FoodEntryItem(
        entry.getId(), item.catalogItemId(), item.name(), item.grams(), item.nutrients().calories(),
        item.nutrients().proteinGrams(), item.nutrients().carbohydrateGrams(),
        item.nutrients().fatGrams(), item.nutrients().fiberGrams())).toList());
    return FoodEntryResponse.from(entry, saved, totals);
  }

  private ItemSnapshot snapshot(AnalyzedFoodItemRequest item) {
    return new ItemSnapshot(null, item.name().trim(), scaled(item.grams()), nutrients(item.calories(),
        item.proteinGrams(), item.carbohydrateGrams(), item.fatGrams(), item.fiberGrams()));
  }

  private FoodNutrients total(List<FoodEntryItem> items) {
    return nutrients(
        items.stream().map(FoodEntryItem::getCalories).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(FoodEntryItem::getProteinGrams).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(FoodEntryItem::getCarbohydrateGrams).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(FoodEntryItem::getFatGrams).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(FoodEntryItem::getFiberGrams).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private FoodNutrients totalSnapshots(List<ItemSnapshot> items) {
    return nutrients(
        items.stream().map(item -> item.nutrients().calories()).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(item -> item.nutrients().proteinGrams()).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(item -> item.nutrients().carbohydrateGrams()).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(item -> item.nutrients().fatGrams()).reduce(BigDecimal.ZERO, BigDecimal::add),
        items.stream().map(item -> item.nutrients().fiberGrams()).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private FoodNutrients nutrients(BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate,
                                  BigDecimal fat, BigDecimal fiber) {
    return new FoodNutrients(scaled(calories), scaled(protein), scaled(carbohydrate), scaled(fat), scaled(fiber));
  }

  private BigDecimal scaled(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private void validateTime(Instant recordedAt) {
    if (recordedAt.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FOOD_TIME_IN_FUTURE", "recordedAt cannot be in the future");
    }
  }

  private String normalized(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void validateThumbnail(String contentType, byte[] thumbnail) {
    if (thumbnail == null || thumbnail.length == 0 || thumbnail.length > 1024 * 1024) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOOD_THUMBNAIL",
          "Thumbnail must be between 1 byte and 1 MB");
    }
    if (!normalizedContentType(contentType).startsWith("image/")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOOD_THUMBNAIL", "Thumbnail must be an image");
    }
  }

  private String normalizedContentType(String value) {
    return value == null || value.isBlank() ? "image/jpeg" : value.toLowerCase();
  }

  private record ItemSnapshot(Long catalogItemId, String name, BigDecimal grams, FoodNutrients nutrients) {
  }
}
