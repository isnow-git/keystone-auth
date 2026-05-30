package com.keystone.auth.infrastructure.security;

import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.domain.model.HashedPassword;
import java.util.Objects;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Argon2id-backed {@link PasswordHasher}. Parameters per ADR-0002: {@code saltLength=16}, {@code
 * hashLength=32}, {@code parallelism=1}, {@code memory=65536 KiB}, {@code iterations=3}. Calibrated
 * for ~250 ms verification on commodity hardware; re-tune after capacity tests.
 */
public final class Argon2PasswordHasher implements PasswordHasher {

  private final Argon2PasswordEncoder encoder;

  public Argon2PasswordHasher() {
    this(new Argon2PasswordEncoder(16, 32, 1, 65536, 3));
  }

  Argon2PasswordHasher(Argon2PasswordEncoder encoder) {
    this.encoder = Objects.requireNonNull(encoder, "encoder");
  }

  @Override
  public HashedPassword hash(String plaintext) {
    Objects.requireNonNull(plaintext, "plaintext");
    return new HashedPassword(encoder.encode(plaintext));
  }

  @Override
  public boolean matches(String plaintext, HashedPassword hash) {
    Objects.requireNonNull(plaintext, "plaintext");
    Objects.requireNonNull(hash, "hash");
    return encoder.matches(plaintext, hash.encoded());
  }
}
