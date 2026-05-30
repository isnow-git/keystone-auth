package com.keystone.auth.application.usecase;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.application.port.RefreshTokenGenerator;
import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.application.port.TokenHasher;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.RefreshTokenValue;
import com.keystone.auth.domain.model.RotationResult;
import com.keystone.auth.domain.model.TokenPair;
import com.keystone.auth.domain.model.User;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rotates a refresh token (ADR-0005).
 *
 * <p>Steps:
 *
 * <ol>
 *   <li>Hash the presented plaintext and look the persisted state up.
 *   <li>Delegate to {@link RefreshToken#rotate} for the state-machine decision.
 *   <li>Persist whichever side of the sealed {@link RotationResult} comes back — mark the consumed
 *       token used and insert the new one (happy path), or revoke the entire family (reuse).
 *   <li>If rotation succeeded, issue a fresh access token for the same user.
 * </ol>
 *
 * <p>Reuse detection emits a structured warning log so operators see thefts in the log pipeline;
 * the response to the client carries no internal detail.
 */
public final class RefreshUseCase {

  private static final Logger log = LoggerFactory.getLogger(RefreshUseCase.class);

  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenHasher tokenHasher;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final AccessTokenIssuer accessTokenIssuer;
  private final UserRepository userRepository;
  private final Clock clock;
  private final Duration refreshTokenTtl;

  public RefreshUseCase(
      RefreshTokenRepository refreshTokenRepository,
      TokenHasher tokenHasher,
      RefreshTokenGenerator refreshTokenGenerator,
      AccessTokenIssuer accessTokenIssuer,
      UserRepository userRepository,
      Clock clock,
      Duration refreshTokenTtl) {
    this.refreshTokenRepository =
        Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
    this.refreshTokenGenerator =
        Objects.requireNonNull(refreshTokenGenerator, "refreshTokenGenerator");
    this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
    if (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
      throw new IllegalArgumentException("refreshTokenTtl must be positive");
    }
  }

  /**
   * Apply the rotation protocol to the supplied plaintext token.
   *
   * @param presentedPlaintext the value the client sent in the refresh cookie
   * @return the sealed outcome — exactly one of {@link RefreshResult.Success}, {@link
   *     RefreshResult.TokenNotFound}, {@link RefreshResult.Expired}, {@link RefreshResult.Revoked},
   *     {@link RefreshResult.ReuseDetected}
   */
  public RefreshResult execute(String presentedPlaintext) {
    Objects.requireNonNull(presentedPlaintext, "presentedPlaintext");
    var presentedHash = tokenHasher.sha256(presentedPlaintext);

    var current = refreshTokenRepository.findByHash(presentedHash).orElse(null);
    if (current == null) {
      return new RefreshResult.TokenNotFound();
    }

    var nextPlaintext = refreshTokenGenerator.generate();
    var nextHash = tokenHasher.sha256(nextPlaintext);
    var now = clock.instant();

    var rotation = current.rotate(nextHash, now, refreshTokenTtl);
    return switch (rotation) {
      case RotationResult.Rotated rotated -> onRotated(rotated, nextPlaintext);
      case RotationResult.ReuseDetected reuse -> onReuse(current, reuse);
      case RotationResult.Expired ignored -> new RefreshResult.Expired();
      case RotationResult.Revoked ignored -> new RefreshResult.Revoked();
    };
  }

  private RefreshResult onRotated(RotationResult.Rotated rotated, String nextPlaintext) {
    refreshTokenRepository.markUsed(rotated.consumed());
    refreshTokenRepository.save(rotated.issued());

    var user = userRepository.findById(rotated.issued().userId()).orElse(null);
    if (user == null) {
      // Defensive: ON DELETE CASCADE means the refresh token row should never outlive the user, so
      // this can only fire on a race with account deletion. Treat as "not found" to the client and
      // avoid issuing tokens against a missing principal.
      log.warn(
          "Refresh rotated for user {} but the user could not be loaded",
          rotated.issued().userId().value());
      return new RefreshResult.TokenNotFound();
    }
    return new RefreshResult.Success(buildTokenPair(rotated, nextPlaintext, user));
  }

  private TokenPair buildTokenPair(
      RotationResult.Rotated rotated, String nextPlaintext, User user) {
    var accessToken = accessTokenIssuer.issue(user.id(), user.roles());
    var refreshValue =
        new RefreshTokenValue(
            nextPlaintext, rotated.issued().tokenHash(), rotated.issued().expiresAt());
    return new TokenPair(accessToken, refreshValue);
  }

  private RefreshResult onReuse(RefreshToken current, RotationResult.ReuseDetected reuse) {
    refreshTokenRepository.revokeFamily(reuse.compromisedFamily());
    log.warn(
        "Refresh token reuse detected: family={} user={} tokenId={} — revoking entire family",
        reuse.compromisedFamily().value(),
        current.userId().value(),
        current.id().value());
    return new RefreshResult.ReuseDetected(reuse.compromisedFamily());
  }
}
