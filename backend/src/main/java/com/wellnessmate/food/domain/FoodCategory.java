package com.wellnessmate.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_categories")
public class FoodCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(name = "name_cn", nullable = false, length = 60)
  private String nameCn;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected FoodCategory() {}

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getNameCn() { return nameCn; }
  public int getSortOrder() { return sortOrder; }
}
