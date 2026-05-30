package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> UserId.of(null));
  }

  @Test
  void wrapsUuid() {
    var uuid = UUID.randomUUID();
    assertThat(UserId.of(uuid).value()).isEqualTo(uuid);
  }

  @Test
  void randomGeneratesDistinctIds() {
    assertThat(UserId.random()).isNotEqualTo(UserId.random());
  }
}
