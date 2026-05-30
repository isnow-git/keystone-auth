package com.keystone.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Plaintext refresh token returned to the client and never persisted in this form. Pairs the opaque
 * value with the matching hash so callers can hand the value to the client and the hash to
 * persistence without recomputing.
 */
public record RefreshTokenValue(String value, TokenHash hash, Instant expiresAt) {

  public RefreshTokenValue {
    Objects.requireNonNull(value, "refresh token value must not be null");
    Objects.requireNonNull(hash, "refresh token hash must not be null");
    Objects.requireNonNull(expiresAt, "refresh token expiry must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("refresh token value must not be blank");
    }
  }

  @Override
  public String toString() {
    return "RefreshTokenValue[***, expiresAt=" + expiresAt + "]";
  }
}
