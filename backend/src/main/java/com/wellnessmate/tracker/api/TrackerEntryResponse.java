package com.wellnessmate.tracker.api;

import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerSource;
import com.wellnessmate.tracker.domain.TrackerType;
import java.math.BigDecimal;
import java.time.Instant;

/** Tracker entry returned to its owner. @author TODO(team member) */
public record TrackerEntryResponse(
    Long id,
    TrackerType type,
    Instant recordedAt,
    BigDecimal amount,
    String unit,
    String detail,
    String notes,
    TrackerSource source,
    long version
) {
  public static TrackerEntryResponse from(TrackerEntry entry) {
    return new TrackerEntryResponse(entry.getId(), entry.getTrackerType(), entry.getRecordedAt(),
        entry.getAmount(), entry.getTrackerType().unit(), entry.getDetail(), entry.getNotes(),
        entry.getSource(), entry.getVersion());
  }
}
