package com.keystone.auth.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single chokepoint for outgoing error responses. Every branch produces RFC 7807 problem details
 * with a generic, non-echoing message; no stack trace is ever serialised.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.debug("Validation failed for {}: {}", request.getRequestURI(), ex.getMessage());
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request payload is invalid");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.debug("Unreadable body on {}: {}", request.getRequestURI(), ex.getMessage());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Request body could not be parsed");
  }

  @ExceptionHandler(Throwable.class)
  public ProblemDetail handleUncaught(Throwable ex, HttpServletRequest request) {
    // Generic 500 — the actual exception goes to the log pipeline only.
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}
