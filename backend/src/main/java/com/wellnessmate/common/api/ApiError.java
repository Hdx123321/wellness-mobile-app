package com.wellnessmate.common.api;

import java.time.Instant;
import java.util.Map;

/** Consistent JSON error response. @author TODO(team member) */
public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    Map<String, String> fieldErrors
) {
  public static ApiError of(int status, String code, String message, String path) {
    return new ApiError(Instant.now(), status, code, message, path, Map.of());
  }
}
