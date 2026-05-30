package com.keystone.auth.application.support;

import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.domain.model.TokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Real SHA-256 hasher for tests. Mirrors the production adapter precisely so use-case tests
 * exercise the same hashing the integration tests will.
 */
public final class Sha256Hasher implements TokenHasher {

  @Override
  public TokenHash sha256(String plaintext) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
      return new TokenHash(HexFormat.of().formatHex(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
    }
  }
}
