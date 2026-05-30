package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoleTest {

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new Role(null));
  }

  @Test
  void rejectsBlank() {
    assertThatThrownBy(() -> new Role("   ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void prependsRolePrefixWhenMissing() {
    assertThat(new Role("user").name()).isEqualTo("ROLE_USER");
  }

  @Test
  void leavesRolePrefixIntactWhenPresent() {
    assertThat(new Role("role_admin").name()).isEqualTo("ROLE_ADMIN");
  }

  @Test
  void rejectsInvalidCharacters() {
    assertThatThrownBy(() -> new Role("hello world"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ROLE_");
  }

  @Test
  void constantsAreReadable() {
    assertThat(Role.USER.name()).isEqualTo("ROLE_USER");
    assertThat(Role.ADMIN.name()).isEqualTo("ROLE_ADMIN");
  }
}
