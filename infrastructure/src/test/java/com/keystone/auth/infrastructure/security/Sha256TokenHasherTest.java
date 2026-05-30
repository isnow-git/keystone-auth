package com.keystone.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class Sha256TokenHasherTest {

  private final Sha256TokenHasher hasher = new Sha256TokenHasher();

  @Test
  void producesKnownVectorForAbc() {
    // SHA-256("abc") known vector from FIPS 180-4 Appendix B.
    var expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    assertThat(hasher.sha256("abc").value()).isEqualTo(expected);
  }

  @Test
  void isDeterministic() {
    assertThat(hasher.sha256("same-input")).isEqualTo(hasher.sha256("same-input"));
  }

  @Test
  void differentInputsProduceDifferentHashes() {
    assertThat(hasher.sha256("input-a")).isNotEqualTo(hasher.sha256("input-b"));
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> hasher.sha256(null));
  }
}
