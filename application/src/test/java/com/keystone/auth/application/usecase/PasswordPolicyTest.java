package com.keystone.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  @ParameterizedTest
  @NullAndEmptySource
  void rejectsEmpty(String input) {
    assertThat(PasswordPolicy.validate(input)).isInstanceOf(PasswordPolicy.Invalid.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"short", "elevenchars"})
  void rejectsShort(String input) {
    var result = PasswordPolicy.validate(input);
    assertThat(result).isInstanceOf(PasswordPolicy.Invalid.class);
    assertThat(((PasswordPolicy.Invalid) result).reason()).contains("12");
  }

  @Test
  void rejectsTooLong() {
    var result = PasswordPolicy.validate("x".repeat(129));
    assertThat(result).isInstanceOf(PasswordPolicy.Invalid.class);
    assertThat(((PasswordPolicy.Invalid) result).reason()).contains("128");
  }

  @Test
  void rejectsControlCharacters() {
    var result = PasswordPolicy.validate("twelve-chars-bell");
    assertThat(result).isInstanceOf(PasswordPolicy.Invalid.class);
  }

  @Test
  void acceptsMinLength() {
    assertThat(PasswordPolicy.validate("aaaaaaaaaaaa")).isInstanceOf(PasswordPolicy.Valid.class);
  }

  @Test
  void acceptsLongPassphrase() {
    var input = "correct horse battery staple — and one more";
    assertThat(PasswordPolicy.validate(input)).isInstanceOf(PasswordPolicy.Valid.class);
  }
}
