package com.keystone.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.UserId;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NimbusJoseAccessTokenIssuerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration TTL = Duration.ofMinutes(15);
  private static final String ISSUER = "keystone-auth";
  private static final String KID = "kid-test";

  private RsaKeyMaterial keys;
  private NimbusJoseAccessTokenIssuer issuer;

  @BeforeEach
  void setUp() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair kp = generator.generateKeyPair();
    keys = new RsaKeyMaterial(KID, (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic());
    issuer = new NimbusJoseAccessTokenIssuer(keys, CLOCK, ISSUER, TTL);
  }

  @Test
  void issuesAVerifiableRs256Token() throws Exception {
    var subject = UserId.of(UUID.randomUUID());
    var roles = Set.of(Role.USER, Role.ADMIN);

    var accessToken = issuer.issue(subject, roles);

    var jwt = SignedJWT.parse(accessToken.value());
    assertThat(jwt.verify(new RSASSAVerifier(keys.publicKey()))).isTrue();

    var header = jwt.getHeader();
    assertThat(header.getAlgorithm().getName()).isEqualTo("RS256");
    assertThat(header.getKeyID()).isEqualTo(KID);
    assertThat(header.getType().toString()).isEqualTo("JWT");

    var claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    assertThat(claims.getSubject()).isEqualTo(subject.value().toString());
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
    assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(TTL));

    @SuppressWarnings("unchecked")
    var rolesClaim = (java.util.List<String>) claims.getClaim("roles");
    assertThat(rolesClaim).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void carriesNoPiiInPayload() throws Exception {
    var subject = UserId.of(UUID.randomUUID());
    var accessToken = issuer.issue(subject, Set.of(Role.USER));

    var claims = SignedJWT.parse(accessToken.value()).getJWTClaimsSet();
    // Explicitly assert the absence of common PII fields.
    assertThat(claims.getClaim("email")).isNull();
    assertThat(claims.getClaim("password")).isNull();
    assertThat(claims.getClaim("name")).isNull();
  }

  @Test
  void successiveIssuesProduceDistinctJti() throws Exception {
    var subject = UserId.of(UUID.randomUUID());

    var a = SignedJWT.parse(issuer.issue(subject, Set.of(Role.USER)).value()).getJWTClaimsSet();
    var b = SignedJWT.parse(issuer.issue(subject, Set.of(Role.USER)).value()).getJWTClaimsSet();

    assertThat(a.getJWTID()).isNotEqualTo(b.getJWTID());
  }
}
