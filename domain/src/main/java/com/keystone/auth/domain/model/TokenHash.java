package com.keystone.auth.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * SHA-256 hex digest of a refresh token value. Persisted in place of the plaintext token; a
 * database leak cannot be replayed against {@code /refresh}.
 */
public record TokenHash(String value) {

  private static final int SHA256_HEX_LENGTH = 64;

  public TokenHash {
    Objects.requireNonNull(value, "token hash must not be null");
    var lower = value.toLowerCase(Locale.ROOT);
    if (lower.length() != SHA256_HEX_LENGTH || !lower.matches("[0-9a-f]+")) {
      throw new IllegalArgumentException("token hash must be a 64-char lowercase hex string");
    }
    value = lower;
  }
}
