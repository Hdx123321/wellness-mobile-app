package com.wellnessmate.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected API failures to the documented envelope. @author TODO(team member) */
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> apiException(ApiException error, HttpServletRequest request) {
    return ResponseEntity.status(error.status()).body(ApiError.of(
        error.status().value(), error.code(), error.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    error.getBindingResult().getFieldErrors().forEach(field ->
        fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
    return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "VALIDATION_FAILED",
        "Request validation failed", request.getRequestURI(), fields));
  }
}
