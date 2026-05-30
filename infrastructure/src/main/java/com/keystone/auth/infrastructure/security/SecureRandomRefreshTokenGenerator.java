package com.keystone.auth.infrastructure.security;

import com.keystone.auth.application.port.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 256 bits of entropy, Base64URL-encoded without padding. Cookie-safe, URL-safe, no
 * canonicalisation pitfalls.
 */
public final class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

  private static final int BYTES = 32;

  private final SecureRandom random;

  public SecureRandomRefreshTokenGenerator() {
    this(new SecureRandom());
  }

  SecureRandomRefreshTokenGenerator(SecureRandom random) {
    this.random = random;
  }

  @Override
  public String generate() {
    var buffer = new byte[BYTES];
    random.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }
}
