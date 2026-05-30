package com.keystone.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code keystone.jwt.*} block of {@code application.yml}. Constructor-bound record so
 * the properties are immutable once Spring is done with them.
 *
 * @param issuer iss claim
 * @param keyId kid header
 * @param accessTokenTtl lifetime of access tokens (default 15m)
 * @param refreshTokenTtl lifetime of refresh tokens (default 7d)
 * @param privateKeyPath filesystem path to the RS256 private key (PEM, PKCS#8)
 * @param publicKeyPath filesystem path to the RS256 public key (PEM, X.509 SubjectPublicKeyInfo)
 */
@ConfigurationProperties("keystone.jwt")
public record KeystoneJwtProperties(
    String issuer,
    String keyId,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String privateKeyPath,
    String publicKeyPath) {}
