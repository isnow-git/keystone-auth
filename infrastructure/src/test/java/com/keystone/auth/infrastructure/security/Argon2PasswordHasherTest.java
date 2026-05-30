package com.keystone.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class Argon2PasswordHasherTest {

  // Use the smallest-allowed parameters so the test suite stays fast; production tunes via the
  // no-arg constructor (see ADR-0002).
  private final Argon2PasswordHasher hasher =
      new Argon2PasswordHasher(new Argon2PasswordEncoder(8, 16, 1, 1024, 1));

  @Test
  void producesPhcFormattedHash() {
    var hashed = hasher.hash("correct-horse-battery-staple");
    assertThat(hashed.encoded()).startsWith("$argon2id$");
  }

  @Test
  void matchesItsOwnOutput() {
    var hashed = hasher.hash("correct-horse-battery-staple");
    assertThat(hasher.matches("correct-horse-battery-staple", hashed)).isTrue();
  }

  @Test
  void rejectsWrongPassword() {
    var hashed = hasher.hash("correct-horse-battery-staple");
    assertThat(hasher.matches("something-else-entirely", hashed)).isFalse();
  }

  @Test
  void twoHashesOfTheSamePlaintextDiffer() {
    var first = hasher.hash("same-input");
    var second = hasher.hash("same-input");
    assertThat(first.encoded()).isNotEqualTo(second.encoded());
    assertThat(hasher.matches("same-input", first)).isTrue();
    assertThat(hasher.matches("same-input", second)).isTrue();
  }

  @Test
  void rejectsNullArguments() {
    assertThatNullPointerException().isThrownBy(() -> hasher.hash(null));
    assertThatNullPointerException()
        .isThrownBy(() -> hasher.matches(null, hasher.hash("x".repeat(12))));
    assertThatNullPointerException().isThrownBy(() -> hasher.matches("x", null));
  }
}
