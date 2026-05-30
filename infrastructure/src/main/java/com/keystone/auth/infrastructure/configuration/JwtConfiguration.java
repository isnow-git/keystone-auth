package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.application.port.RefreshTokenGenerator;
import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.infrastructure.security.KeystoneJwtProperties;
import com.keystone.auth.infrastructure.security.NimbusJoseAccessTokenIssuer;
import com.keystone.auth.infrastructure.security.RsaKeyMaterial;
import com.keystone.auth.infrastructure.security.SecureRandomRefreshTokenGenerator;
import com.keystone.auth.infrastructure.security.Sha256TokenHasher;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JWT signing + refresh-token primitives. */
@Configuration
@EnableConfigurationProperties(KeystoneJwtProperties.class)
public class JwtConfiguration {

  @Bean
  public RsaKeyMaterial rsaKeyMaterial(KeystoneJwtProperties properties) {
    return RsaKeyMaterial.load(
        properties.keyId(),
        Path.of(properties.privateKeyPath()),
        Path.of(properties.publicKeyPath()));
  }

  @Bean
  public AccessTokenIssuer accessTokenIssuer(
      RsaKeyMaterial keys, Clock clock, KeystoneJwtProperties properties) {
    return new NimbusJoseAccessTokenIssuer(
        keys, clock, properties.issuer(), properties.accessTokenTtl());
  }

  @Bean
  public RefreshTokenGenerator refreshTokenGenerator() {
    return new SecureRandomRefreshTokenGenerator();
  }

  @Bean
  public TokenHasher tokenHasher() {
    return new Sha256TokenHasher();
  }
}
