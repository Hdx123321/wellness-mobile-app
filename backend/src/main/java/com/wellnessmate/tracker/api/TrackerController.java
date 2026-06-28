package com.wellnessmate.tracker.api;

import com.wellnessmate.common.api.PageResponse;
import com.wellnessmate.tracker.domain.TrackerType;
import com.wellnessmate.tracker.service.TrackerService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** JWT-owned tracker catalog and entry API. @author TODO(team member) */
@RestController
@RequestMapping("/api")
public class TrackerController {
  private final TrackerService trackers;

  public TrackerController(TrackerService trackers) {
    this.trackers = trackers;
  }

  @GetMapping("/trackers/types")
  public List<TrackerTypeResponse> types() {
    return trackers.types();
  }

  @PostMapping("/tracker-entries")
  @ResponseStatus(HttpStatus.CREATED)
  public TrackerEntryResponse create(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody TrackerEntryRequest request) {
    return trackers.create(userId(jwt), request);
  }

  @GetMapping("/tracker-entries")
  public PageResponse<TrackerEntryResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) TrackerType type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return trackers.list(userId(jwt), type, from, to, page, size);
  }

  @GetMapping("/tracker-entries/{id}")
  public TrackerEntryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return trackers.get(userId(jwt), id);
  }

  @PutMapping("/tracker-entries/{id}")
  public TrackerEntryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody TrackerEntryRequest request) {
    return trackers.update(userId(jwt), id, request);
  }

  @DeleteMapping("/tracker-entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    trackers.delete(userId(jwt), id);
  }

  private Long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
