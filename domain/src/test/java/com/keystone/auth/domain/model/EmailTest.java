package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> Email.of(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void rejectsBlank(String input) {
    assertThatThrownBy(() -> Email.of(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-an-email",
        "missing@tld",
        "@nodomain.com",
        "no-at-sign.com",
        "spaces in@mail.com",
        "two@@signs.com"
      })
  void rejectsMalformed(String input) {
    assertThatThrownBy(() -> Email.of(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid");
  }

  @Test
  void rejectsTooLong() {
    var local = "a".repeat(250);
    assertThatThrownBy(() -> Email.of(local + "@b.co"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("254");
  }

  @Test
  void normalisesToLowerCaseAndTrims() {
    var email = Email.of("  Alice@Example.COM  ");
    assertThat(email.value()).isEqualTo("alice@example.com");
  }

  @Test
  void valueObjectEquality() {
    assertThat(Email.of("a@b.co")).isEqualTo(Email.of("A@B.CO"));
    assertThat(Email.of("a@b.co")).hasSameHashCodeAs(Email.of("A@B.CO"));
  }
}
