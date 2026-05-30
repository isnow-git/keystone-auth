package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenTest {

  @Test
  void rejectsNullValue() {
    assertThatNullPointerException().isThrownBy(() -> new AccessToken(null, Instant.now()));
  }

  @Test
  void rejectsNullExpiry() {
    assertThatNullPointerException().isThrownBy(() -> new AccessToken("token", null));
  }

  @Test
  void rejectsBlankValue() {
    assertThatThrownBy(() -> new AccessToken("  ", Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringMasksValue() {
    var token = new AccessToken("super-secret-jwt", Instant.parse("2030-01-01T00:00:00Z"));
    assertThat(token.toString()).doesNotContain("super-secret-jwt");
    assertThat(token.toString()).contains("***");
    assertThat(token.toString()).contains("2030");
  }
}
