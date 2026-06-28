package com.wellnessmate.tracker.api;

import com.wellnessmate.tracker.domain.TrackerType;
import java.math.BigDecimal;

/** UI metadata for one built-in tracker type. @author TODO(team member) */
public record TrackerTypeResponse(
    TrackerType type,
    String unit,
    String amountLabel,
    String detailLabel,
    boolean detailRequired,
    BigDecimal minimum,
    BigDecimal maximum,
    boolean integerOnly
) {
  public static TrackerTypeResponse from(TrackerType type) {
    return new TrackerTypeResponse(type, type.unit(), type.amountLabel(), type.detailLabel(),
        type.detailRequired(), type.minimum(), type.maximum(), type.integerOnly());
  }
}
