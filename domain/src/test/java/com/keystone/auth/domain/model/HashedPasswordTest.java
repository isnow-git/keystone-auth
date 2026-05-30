package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HashedPasswordTest {

  private static final String VALID = "$argon2id$v=19$m=65536,t=3,p=1$abc$def";

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new HashedPassword(null));
  }

  @Test
  void rejectsBlank() {
    assertThatThrownBy(() -> new HashedPassword("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsValuesThatLookLikePlaintext() {
    assertThatThrownBy(() -> new HashedPassword("hunter2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PHC");
  }

  @Test
  void acceptsPhcFormat() {
    assertThat(new HashedPassword(VALID).encoded()).isEqualTo(VALID);
  }

  @Test
  void toStringMasksSecret() {
    assertThat(new HashedPassword(VALID).toString()).doesNotContain(VALID);
    assertThat(new HashedPassword(VALID).toString()).contains("***");
  }
}
