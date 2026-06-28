package com.wellnessmate.tracker.service;

import com.wellnessmate.common.api.ApiException;
import com.wellnessmate.common.api.PageResponse;
import com.wellnessmate.tracker.api.TrackerEntryRequest;
import com.wellnessmate.tracker.api.TrackerEntryResponse;
import com.wellnessmate.tracker.api.TrackerTypeResponse;
import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.repository.TrackerEntryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Built-in tracker catalog and owned entry use cases. @author TODO(team member) */
@Service
public class TrackerService {
  private final TrackerEntryRepository entries;

  public TrackerService(TrackerEntryRepository entries) {
    this.entries = entries;
  }

  public List<TrackerTypeResponse> types() {
    return Arrays.stream(TrackerType.values()).map(TrackerTypeResponse::from).toList();
  }

  @Transactional
  public TrackerEntryResponse create(Long userId, TrackerEntryRequest request) {
    validate(request);
    TrackerEntry entry = new TrackerEntry(userId, request.type(), request.recordedAt(),
        normalizedAmount(request.amount()), normalized(request.detail()), normalized(request.notes()));
    return TrackerEntryResponse.from(entries.save(entry));
  }

  @Transactional(readOnly = true)
  public TrackerEntryResponse get(Long userId, Long id) {
    return TrackerEntryResponse.from(requireOwned(userId, id));
  }

  @Transactional(readOnly = true)
  public PageResponse<TrackerEntryResponse> list(Long userId, TrackerType type, Instant from,
                                                 Instant to, int page, int size) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from must be before to");
    }
    PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
        Sort.by(Sort.Direction.DESC, "recordedAt", "id"));
    Page<TrackerEntry> result = entries.findOwned(userId, type, from, to, pageable);
    return PageResponse.from(result, result.stream().map(TrackerEntryResponse::from).toList());
  }

  @Transactional
  public TrackerEntryResponse update(Long userId, Long id, TrackerEntryRequest request) {
    validate(request);
    TrackerEntry entry = requireOwned(userId, id);
    entry.update(request.type(), request.recordedAt(), normalizedAmount(request.amount()),
        normalized(request.detail()), normalized(request.notes()));
    return TrackerEntryResponse.from(entry);
  }

  @Transactional
  public void delete(Long userId, Long id) {
    entries.delete(requireOwned(userId, id));
  }

  private void validate(TrackerEntryRequest request) {
    TrackerType type = request.type();
    BigDecimal amount = request.amount();
    if (amount.compareTo(type.minimum()) < 0 || amount.compareTo(type.maximum()) > 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKER_AMOUNT_OUT_OF_RANGE",
          "Amount for " + type + " must be between " + type.minimum() + " and " + type.maximum());
    }
    if (type.integerOnly() && amount.stripTrailingZeros().scale() > 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKER_AMOUNT_MUST_BE_INTEGER",
          "Amount for " + type + " must be a whole number");
    }
    if (type.detailRequired() && (request.detail() == null || request.detail().isBlank())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKER_DETAIL_REQUIRED",
          "Detail is required for " + type);
    }
    if (request.recordedAt().isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKER_TIME_IN_FUTURE",
          "recordedAt cannot be in the future");
    }
  }

  private TrackerEntry requireOwned(Long userId, Long id) {
    return entries.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRACKER_ENTRY_NOT_FOUND",
            "Tracker entry not found"));
  }

  private BigDecimal normalizedAmount(BigDecimal amount) {
    return amount.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private String normalized(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
