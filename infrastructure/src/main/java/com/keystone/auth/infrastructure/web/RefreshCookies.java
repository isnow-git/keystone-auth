package com.keystone.auth.infrastructure.web;

import com.keystone.auth.domain.model.RefreshTokenValue;
import java.time.Instant;
import org.springframework.http.ResponseCookie;

/**
 * Centralised refresh-token cookie construction. Keeps the security attributes ({@code HttpOnly},
 * {@code Secure}, {@code SameSite=Strict}, {@code Path=/auth/refresh}) in one place so every
 * endpoint sets them identically.
 */
final class RefreshCookies {

  static final String NAME = "refresh_token";
  static final String PATH = "/auth/refresh";

  private RefreshCookies() {}

  static ResponseCookie freshCookie(RefreshTokenValue token, Instant now) {
    var maxAge = java.time.Duration.between(now, token.expiresAt());
    return ResponseCookie.from(NAME, token.value())
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(PATH)
        .maxAge(maxAge.isNegative() ? java.time.Duration.ZERO : maxAge)
        .build();
  }

  /** A cookie with {@code Max-Age=0} that clears the existing one on the client. */
  static ResponseCookie clearingCookie() {
    return ResponseCookie.from(NAME, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(PATH)
        .maxAge(0)
        .build();
  }
}
