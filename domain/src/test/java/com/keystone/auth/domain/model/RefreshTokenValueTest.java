package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenValueTest {

  private static final TokenHash HASH = new TokenHash("0".repeat(64));

  @Test
  void rejectsNullValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RefreshTokenValue(null, HASH, Instant.now()));
  }

  @Test
  void rejectsNullHash() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RefreshTokenValue("v", null, Instant.now()));
  }

  @Test
  void rejectsBlankValue() {
    assertThatThrownBy(() -> new RefreshTokenValue("  ", HASH, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringMasksValue() {
    var token = new RefreshTokenValue("plaintext", HASH, Instant.parse("2030-01-01T00:00:00Z"));
    assertThat(token.toString()).doesNotContain("plaintext");
    assertThat(token.toString()).contains("***");
  }
}
