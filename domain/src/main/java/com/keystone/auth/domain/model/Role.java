package com.keystone.auth.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Granted authority string. Stored as text so new roles can be added without a schema change;
 * normalisation (upper-case, {@code ROLE_} prefix) is enforced at construction.
 */
public record Role(String name) {

  public Role {
    Objects.requireNonNull(name, "role name must not be null");
    var normalised = name.trim().toUpperCase(Locale.ROOT);
    if (normalised.isEmpty()) {
      throw new IllegalArgumentException("role name must not be blank");
    }
    if (!normalised.startsWith("ROLE_")) {
      normalised = "ROLE_" + normalised;
    }
    if (!normalised.matches("ROLE_[A-Z0-9_]+")) {
      throw new IllegalArgumentException("role name must match ROLE_[A-Z0-9_]+ — got: " + name);
    }
    name = normalised;
  }

  public static final Role USER = new Role("USER");
  public static final Role ADMIN = new Role("ADMIN");
}
