package com.keystone.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Groups every refresh token that descends from a single {@code /login} call. Reuse detection
 * operates at the family level: presenting an already-used token revokes every member.
 */
public record FamilyId(UUID value) {

  public FamilyId {
    Objects.requireNonNull(value, "family id must not be null");
  }

  public static FamilyId random() {
    return new FamilyId(UUID.randomUUID());
  }
}
