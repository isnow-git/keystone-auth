package com.keystone.auth.application.usecase;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.application.port.RefreshTokenGenerator;
import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.domain.model.AccessToken;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.RefreshTokenValue;
import com.keystone.auth.domain.model.TokenPair;
import com.keystone.auth.domain.model.User;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Authenticates a user and mints a fresh token pair.
 *
 * <p>Two failure modes — unknown email and wrong password — collapse to the same {@link
 * LoginResult.InvalidCredentials} value so the REST adapter cannot accidentally leak which one
 * applied (no enumeration).
 *
 * <p>The refresh token is generated and hashed once: the plaintext is returned to the client
 * (cookie), the hash is persisted with a fresh {@link com.keystone.auth.domain.model.FamilyId}.
 */
public final class LoginUseCase {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final AccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final TokenHasher tokenHasher;
  private final RefreshTokenRepository refreshTokenRepository;
  private final Clock clock;
  private final Duration refreshTokenTtl;

  public LoginUseCase(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      AccessTokenIssuer accessTokenIssuer,
      RefreshTokenGenerator refreshTokenGenerator,
      TokenHasher tokenHasher,
      RefreshTokenRepository refreshTokenRepository,
      Clock clock,
      Duration refreshTokenTtl) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
    this.refreshTokenGenerator =
        Objects.requireNonNull(refreshTokenGenerator, "refreshTokenGenerator");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
    this.refreshTokenRepository =
        Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
    if (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
      throw new IllegalArgumentException("refreshTokenTtl must be positive");
    }
  }

  /**
   * Verify the credentials, mint and persist a refresh token, return both tokens to the caller.
   *
   * @param rawEmail email submitted by the client
   * @param rawPassword plaintext password submitted by the client
   * @return {@link LoginResult.Success} on a verified login, otherwise {@link
   *     LoginResult.InvalidCredentials}
   */
  public LoginResult execute(String rawEmail, String rawPassword) {
    Email email;
    try {
      email = Email.of(rawEmail);
    } catch (RuntimeException e) {
      return new LoginResult.InvalidCredentials();
    }

    var user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      return new LoginResult.InvalidCredentials();
    }
    if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
      return new LoginResult.InvalidCredentials();
    }

    return new LoginResult.Success(user.id(), mintTokens(user));
  }

  private TokenPair mintTokens(User user) {
    var now = clock.instant();

    AccessToken accessToken = accessTokenIssuer.issue(user.id(), user.roles());

    String refreshPlaintext = refreshTokenGenerator.generate();
    var refreshHash = tokenHasher.sha256(refreshPlaintext);

    RefreshToken persisted =
        RefreshToken.issueInitial(user.id(), refreshHash, now, refreshTokenTtl);
    refreshTokenRepository.save(persisted);

    var refreshValue = new RefreshTokenValue(refreshPlaintext, refreshHash, persisted.expiresAt());
    return new TokenPair(accessToken, refreshValue);
  }
}
