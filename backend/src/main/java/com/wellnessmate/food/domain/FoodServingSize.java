package com.wellnessmate.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "food_serving_sizes")
public class FoodServingSize {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "catalog_item_id", nullable = false)
  private Long catalogItemId;

  @Column(nullable = false, length = 60)
  private String label;

  @Column(name = "label_cn", nullable = false, length = 60)
  private String labelCn;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal grams;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected FoodServingSize() {}

  public Long getId() { return id; }
  public Long getCatalogItemId() { return catalogItemId; }
  public String getLabel() { return label; }
  public String getLabelCn() { return labelCn; }
  public BigDecimal getGrams() { return grams; }
  public boolean isDefault() { return isDefault; }
  public int getSortOrder() { return sortOrder; }
}
