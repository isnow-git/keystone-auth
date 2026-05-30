package com.keystone.auth.application.usecase;

import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.application.port.TokenHasher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Revokes the family of the presented refresh token.
 *
 * <p>Idempotent by design: the REST adapter always responds {@code 204 No Content}, regardless of
 * whether the cookie carried a valid token. That choice prevents an attacker from probing the
 * existence of an account via the logout endpoint.
 *
 * <p>The handler logs whether a family was actually revoked so operators can correlate logout
 * activity in observability tooling.
 */
public final class LogoutUseCase {

  private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);

  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenHasher tokenHasher;

  public LogoutUseCase(RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher) {
    this.refreshTokenRepository =
        Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
  }

  /**
   * Apply the logout: hash the presented plaintext, revoke the matching family if it exists. A
   * {@code null} or unknown value is intentionally silent — see the class-level javadoc.
   *
   * @param presentedPlaintext the value from the refresh cookie, possibly {@code null}
   */
  public void execute(String presentedPlaintext) {
    if (presentedPlaintext == null) {
      log.debug("Logout called with no refresh cookie — treating as a no-op");
      return;
    }
    var hash = tokenHasher.sha256(presentedPlaintext);
    var token = refreshTokenRepository.findByHash(hash).orElse(null);
    if (token == null) {
      log.debug("Logout: presented token not found — no-op");
      return;
    }
    refreshTokenRepository.revokeFamily(token.familyId());
    log.info(
        "Logout: revoked family={} user={} via tokenId={}",
        token.familyId().value(),
        token.userId().value(),
        token.id().value());
  }
}
