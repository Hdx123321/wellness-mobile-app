package com.wellnessmate.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Application-owned food reference values expressed per 100 grams. */
@Entity
@Table(name = "food_catalog_items")
public class FoodCatalogItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 500)
  private String searchTerms;

  @Column(name = "calories_per_100g", nullable = false, precision = 8, scale = 2)
  private BigDecimal caloriesPer100g;

  @Column(name = "protein_per_100g", nullable = false, precision = 8, scale = 2)
  private BigDecimal proteinPer100g;

  @Column(name = "carbohydrate_per_100g", nullable = false, precision = 8, scale = 2)
  private BigDecimal carbohydratePer100g;

  @Column(name = "fat_per_100g", nullable = false, precision = 8, scale = 2)
  private BigDecimal fatPer100g;

  @Column(name = "fiber_per_100g", nullable = false, precision = 8, scale = 2)
  private BigDecimal fiberPer100g;

  @Column(name = "category_id")
  private Long categoryId;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  protected FoodCatalogItem() {
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public BigDecimal getCaloriesPer100g() { return caloriesPer100g; }
  public BigDecimal getProteinPer100g() { return proteinPer100g; }
  public BigDecimal getCarbohydratePer100g() { return carbohydratePer100g; }
  public BigDecimal getFatPer100g() { return fatPer100g; }
  public BigDecimal getFiberPer100g() { return fiberPer100g; }
  public Long getCategoryId() { return categoryId; }
  public String getImageUrl() { return imageUrl; }
}
