package com.keystone.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Type-safe identifier for a persisted {@link RefreshToken}. */
public record RefreshTokenId(UUID value) {

  public RefreshTokenId {
    Objects.requireNonNull(value, "refresh token id must not be null");
  }

  public static RefreshTokenId random() {
    return new RefreshTokenId(UUID.randomUUID());
  }
}
