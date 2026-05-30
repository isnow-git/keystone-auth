package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.application.port.RefreshTokenGenerator;
import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.application.usecase.LoginUseCase;
import com.keystone.auth.application.usecase.LogoutUseCase;
import com.keystone.auth.application.usecase.RefreshUseCase;
import com.keystone.auth.application.usecase.RegisterUseCase;
import com.keystone.auth.infrastructure.security.KeystoneJwtProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the use cases. Use-case classes themselves stay free of Spring annotations. */
@Configuration
public class ApplicationConfiguration {

  @Bean
  public RegisterUseCase registerUseCase(
      UserRepository userRepository, PasswordHasher passwordHasher, Clock clock) {
    return new RegisterUseCase(userRepository, passwordHasher, clock);
  }

  @Bean
  public LoginUseCase loginUseCase(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      AccessTokenIssuer accessTokenIssuer,
      RefreshTokenGenerator refreshTokenGenerator,
      TokenHasher tokenHasher,
      RefreshTokenRepository refreshTokenRepository,
      Clock clock,
      KeystoneJwtProperties properties) {
    return new LoginUseCase(
        userRepository,
        passwordHasher,
        accessTokenIssuer,
        refreshTokenGenerator,
        tokenHasher,
        refreshTokenRepository,
        clock,
        properties.refreshTokenTtl());
  }

  @Bean
  public LogoutUseCase logoutUseCase(
      RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher) {
    return new LogoutUseCase(refreshTokenRepository, tokenHasher);
  }

  @Bean
  public RefreshUseCase refreshUseCase(
      RefreshTokenRepository refreshTokenRepository,
      TokenHasher tokenHasher,
      RefreshTokenGenerator refreshTokenGenerator,
      AccessTokenIssuer accessTokenIssuer,
      UserRepository userRepository,
      Clock clock,
      KeystoneJwtProperties properties) {
    return new RefreshUseCase(
        refreshTokenRepository,
        tokenHasher,
        refreshTokenGenerator,
        accessTokenIssuer,
        userRepository,
        clock,
        properties.refreshTokenTtl());
  }
}
