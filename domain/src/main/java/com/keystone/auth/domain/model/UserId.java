package com.keystone.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Type-safe identifier for a {@link User}. */
public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "user id must not be null");
  }

  public static UserId of(UUID value) {
    return new UserId(value);
  }

  public static UserId random() {
    return new UserId(UUID.randomUUID());
  }
}
