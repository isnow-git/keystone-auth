package com.keystone.auth.application.port;

/**
 * Produces a cryptographically random plaintext refresh-token value. Production adapter uses {@link
 * java.security.SecureRandom} and Base64URL encodes 32 bytes (256 bits) of entropy.
 */
public interface RefreshTokenGenerator {

  /** A fresh, URL-safe plaintext value to hand to the client. */
  String generate();
}
