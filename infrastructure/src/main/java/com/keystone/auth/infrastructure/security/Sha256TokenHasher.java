package com.keystone.auth.infrastructure.security;

import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.domain.model.TokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 of the UTF-8 bytes, returned as a 64-char lowercase hex string. */
public final class Sha256TokenHasher implements TokenHasher {

  @Override
  public TokenHash sha256(String plaintext) {
    Objects.requireNonNull(plaintext, "plaintext");
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
      return new TokenHash(HexFormat.of().formatHex(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
    }
  }
}
