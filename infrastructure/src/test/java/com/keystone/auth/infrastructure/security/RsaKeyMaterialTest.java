package com.keystone.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RsaKeyMaterialTest {

  private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static Path writePem(Path dir, String name, String label, byte[] bytes)
      throws IOException {
    var encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes);
    var pem = "-----BEGIN " + label + "-----\n" + encoded + "\n-----END " + label + "-----\n";
    var path = dir.resolve(name);
    Files.writeString(path, pem);
    return path;
  }

  @Test
  void roundTripsAGeneratedKeyPair(@TempDir Path tempDir) throws Exception {
    var keyPair = generateKeyPair();
    var privatePath =
        writePem(tempDir, "private.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());
    var publicPath =
        writePem(tempDir, "public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded());

    var material = RsaKeyMaterial.load("kid-test", privatePath, publicPath);

    assertThat(material.keyId()).isEqualTo("kid-test");
    assertThat(material.privateKey())
        .isInstanceOf(RSAPrivateKey.class)
        .satisfies(
            k ->
                assertThat(k.getModulus())
                    .isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus()));
    assertThat(material.publicKey())
        .isInstanceOf(RSAPublicKey.class)
        .satisfies(
            k ->
                assertThat(k.getModulus())
                    .isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus()));
  }

  @Test
  void surfacesIoErrorsAsIllegalState(@TempDir Path tempDir) {
    var missing = tempDir.resolve("does-not-exist.pem");
    assertThatThrownBy(() -> RsaKeyMaterial.load("k", missing, missing))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankKeyId() throws Exception {
    var keyPair = generateKeyPair();
    assertThatThrownBy(
            () ->
                new RsaKeyMaterial(
                    "", (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
