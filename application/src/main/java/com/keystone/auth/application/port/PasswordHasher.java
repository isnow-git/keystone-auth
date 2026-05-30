package com.keystone.auth.application.port;

import com.keystone.auth.domain.model.HashedPassword;

/**
 * Hashing port. The domain only ever sees the encoded form; raw passwords cross this interface and
 * stop. Production adapter wraps Spring Security's Argon2 encoder (see ADR-0002); tests use a fake
 * that prefixes with {@code $fake$} so the {@link HashedPassword} invariant still holds.
 */
public interface PasswordHasher {

  HashedPassword hash(String plaintext);

  boolean matches(String plaintext, HashedPassword hash);
}
