package com.wellnessmate.tracker.domain;

import java.math.BigDecimal;

/** Canonical built-in tracker definitions and numeric boundaries. @author TODO(team member) */
public enum TrackerType {
  FOOD("kcal", "Calories", "Food or meal", true, decimal("0"), decimal("20000"), false),
  WEIGHT("kg", "Weight", null, false, decimal("20"), decimal("500"), false),
  WORKOUT("min", "Duration", "Workout type", true, decimal("1"), decimal("1440"), false),
  STEPS("steps", "Steps", null, false, decimal("0"), decimal("200000"), true),
  SLEEP("min", "Duration", null, false, decimal("0"), decimal("1440"), false),
  WATER("ml", "Volume", null, false, decimal("0"), decimal("20000"), false);

  private final String unit;
  private final String amountLabel;
  private final String detailLabel;
  private final boolean detailRequired;
  private final BigDecimal minimum;
  private final BigDecimal maximum;
  private final boolean integerOnly;

  TrackerType(String unit, String amountLabel, String detailLabel, boolean detailRequired,
              BigDecimal minimum, BigDecimal maximum, boolean integerOnly) {
    this.unit = unit;
    this.amountLabel = amountLabel;
    this.detailLabel = detailLabel;
    this.detailRequired = detailRequired;
    this.minimum = minimum;
    this.maximum = maximum;
    this.integerOnly = integerOnly;
  }

  public String unit() { return unit; }
  public String amountLabel() { return amountLabel; }
  public String detailLabel() { return detailLabel; }
  public boolean detailRequired() { return detailRequired; }
  public BigDecimal minimum() { return minimum; }
  public BigDecimal maximum() { return maximum; }
  public boolean integerOnly() { return integerOnly; }

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }
}
