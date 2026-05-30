package com.keystone.auth.domain.model;

import java.util.Objects;

/**
 * Opaque wrapper around an encoded password hash (e.g. an Argon2id encoding).
 *
 * <p>The hashing algorithm itself lives behind a port in the application layer; the domain only
 * cares that the value it carries is the encoded form, never plaintext. Construction rejects
 * anything that looks like a raw password.
 */
public record HashedPassword(String encoded) {

  public HashedPassword {
    Objects.requireNonNull(encoded, "encoded password must not be null");
    if (encoded.isBlank()) {
      throw new IllegalArgumentException("encoded password must not be blank");
    }
    if (!encoded.startsWith("$")) {
      throw new IllegalArgumentException(
          "encoded password must be in PHC-style format (starts with '$')");
    }
  }

  /** Avoid leaking the hash via accidental logging / {@code toString}. */
  @Override
  public String toString() {
    return "HashedPassword[***]";
  }
}
