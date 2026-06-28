package com.wellnessmate.tracker.api;

import com.wellnessmate.tracker.domain.TrackerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Create/update payload interpreted according to its tracker type. @author TODO(team member) */
public record TrackerEntryRequest(
    @NotNull TrackerType type,
    @NotNull Instant recordedAt,
    @NotNull @DecimalMin("0") BigDecimal amount,
    @Size(max = 255) String detail,
    @Size(max = 1000) String notes
) {
}
