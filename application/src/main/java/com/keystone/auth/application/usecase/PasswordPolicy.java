package com.keystone.auth.application.usecase;

/**
 * Minimum-viable password policy. Calibrated against the OWASP ASVS v4 password recommendations:
 * length is the dominant factor; reject only what is obviously weak. Anything beyond this (entropy
 * estimation, breach corpora lookup) is a separate adapter behind its own port.
 */
final class PasswordPolicy {

  private static final int MIN_LENGTH = 12;
  private static final int MAX_LENGTH = 128;

  private PasswordPolicy() {}

  sealed interface Result {}

  record Valid() implements Result {}

  record Invalid(String reason) implements Result {}

  static Result validate(String password) {
    if (password == null || password.isEmpty()) {
      return new Invalid("password must not be empty");
    }
    if (password.length() < MIN_LENGTH) {
      return new Invalid("password must be at least " + MIN_LENGTH + " characters");
    }
    if (password.length() > MAX_LENGTH) {
      return new Invalid("password must be at most " + MAX_LENGTH + " characters");
    }
    if (password.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
      return new Invalid("password must not contain control characters");
    }
    return new Valid();
  }
}
