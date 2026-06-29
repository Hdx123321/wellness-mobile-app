package com.wellnessmate.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Nutrient snapshot for one food in a confirmed meal. */
@Entity
@Table(name = "food_entry_items")
public class FoodEntryItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long foodEntryId;

  private Long catalogItemId;

  @Column(nullable = false, length = 120)
  private String foodName;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal grams;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal calories;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal proteinGrams;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal carbohydrateGrams;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal fatGrams;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal fiberGrams;

  protected FoodEntryItem() {
  }

  public FoodEntryItem(Long foodEntryId, Long catalogItemId, String foodName, BigDecimal grams,
                       BigDecimal calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams,
                       BigDecimal fatGrams, BigDecimal fiberGrams) {
    this.foodEntryId = foodEntryId;
    this.catalogItemId = catalogItemId;
    this.foodName = foodName;
    this.grams = grams;
    this.calories = calories;
    this.proteinGrams = proteinGrams;
    this.carbohydrateGrams = carbohydrateGrams;
    this.fatGrams = fatGrams;
    this.fiberGrams = fiberGrams;
  }

  public Long getId() { return id; }
  public Long getFoodEntryId() { return foodEntryId; }
  public Long getCatalogItemId() { return catalogItemId; }
  public String getFoodName() { return foodName; }
  public BigDecimal getGrams() { return grams; }
  public BigDecimal getCalories() { return calories; }
  public BigDecimal getProteinGrams() { return proteinGrams; }
  public BigDecimal getCarbohydrateGrams() { return carbohydrateGrams; }
  public BigDecimal getFatGrams() { return fatGrams; }
  public BigDecimal getFiberGrams() { return fiberGrams; }
}
