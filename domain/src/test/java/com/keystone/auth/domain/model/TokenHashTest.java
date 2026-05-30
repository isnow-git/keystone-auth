package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenHashTest {

  private static final String SHA256_HEX = "a".repeat(64);

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new TokenHash(null));
  }

  @Test
  void rejectsWrongLength() {
    assertThatThrownBy(() -> new TokenHash("abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64");
  }

  @Test
  void rejectsNonHexCharacters() {
    var almostHex = "z".repeat(64);
    assertThatThrownBy(() -> new TokenHash(almostHex)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalisesToLowerCase() {
    var upper = "A".repeat(64);
    assertThat(new TokenHash(upper).value()).isEqualTo(SHA256_HEX);
  }
}
