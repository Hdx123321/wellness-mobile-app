package com.wellnessmate.common.api;

import org.springframework.http.HttpStatus;

/** Stable business exception mapped to the public error envelope. @author TODO(team member) */
public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
}
