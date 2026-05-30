package com.keystone.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A signed RS256 JWT and its expiry. The domain treats the token as opaque; signing and parsing
 * live in the infrastructure layer.
 */
public record AccessToken(String value, Instant expiresAt) {

  public AccessToken {
    Objects.requireNonNull(value, "access token value must not be null");
    Objects.requireNonNull(expiresAt, "access token expiry must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("access token value must not be blank");
    }
  }

  @Override
  public String toString() {
    return "AccessToken[***, expiresAt=" + expiresAt + "]";
  }
}
