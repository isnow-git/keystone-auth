package com.keystone.auth.infrastructure.security;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.domain.model.AccessToken;
import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.UserId;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 JWT issuer (see ADR-0001).
 *
 * <p>Claims: {@code iss}, {@code sub} (user id), {@code jti}, {@code iat}, {@code exp}, {@code
 * roles} (array of granted authority names). No PII — never the email, never the password.
 *
 * <p>Header: {@code alg=RS256}, {@code typ=JWT}, {@code kid} matching the published JWKS.
 */
public final class NimbusJoseAccessTokenIssuer implements AccessTokenIssuer {

  private final RsaKeyMaterial keys;
  private final RSASSASigner signer;
  private final Clock clock;
  private final String issuer;
  private final Duration ttl;

  public NimbusJoseAccessTokenIssuer(
      RsaKeyMaterial keys, Clock clock, String issuer, Duration ttl) {
    this.keys = Objects.requireNonNull(keys, "keys");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = Objects.requireNonNull(issuer, "issuer");
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    if (issuer.isBlank()) {
      throw new IllegalArgumentException("issuer must not be blank");
    }
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    this.signer = new RSASSASigner(keys.privateKey());
  }

  @Override
  public AccessToken issue(UserId subject, Set<Role> roles) {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(roles, "roles");

    var now = clock.instant();
    var exp = now.plus(ttl);

    var header =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(keys.keyId())
            .type(JOSEObjectType.JWT)
            .build();

    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject.value().toString())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .claim("roles", roles.stream().map(Role::name).toList())
            .build();

    var jwt = new SignedJWT(header, claims);
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign access token", e);
    }
    return new AccessToken(jwt.serialize(), exp);
  }
}
