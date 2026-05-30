package com.keystone.auth.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalised, validated email address used as the unique identifier of a {@link User}.
 *
 * <p>The regex is intentionally permissive — RFC 5322 is too lax to be useful as a security gate
 * and too strict to match real-world inputs. The intent is to reject obvious typos at the boundary;
 * deeper validation happens on send (e.g. confirmation email).
 */
public record Email(String value) {

  private static final int MAX_LENGTH = 254;

  private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  public Email {
    Objects.requireNonNull(value, "email value must not be null");
    var trimmed = value.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("email must not be blank");
    }
    if (trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("email must be at most " + MAX_LENGTH + " characters");
    }
    if (!PATTERN.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("email is not a valid address");
    }
    value = trimmed;
  }

  public static Email of(String raw) {
    return new Email(raw);
  }
}
