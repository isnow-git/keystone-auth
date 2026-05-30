package com.keystone.auth.application.support;

import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.domain.model.HashedPassword;
import java.util.Objects;

/**
 * Deterministic hasher for tests. Prefixes the plaintext with {@code $fake$} so the result still
 * satisfies the {@link HashedPassword} PHC-format invariant. Equality of plaintext, not a real
 * hash, governs {@link #matches}.
 */
public final class FakePasswordHasher implements PasswordHasher {

  private static final String PREFIX = "$fake$";

  @Override
  public HashedPassword hash(String plaintext) {
    Objects.requireNonNull(plaintext, "plaintext");
    return new HashedPassword(PREFIX + plaintext);
  }

  @Override
  public boolean matches(String plaintext, HashedPassword hash) {
    return hash.encoded().equals(PREFIX + plaintext);
  }
}
