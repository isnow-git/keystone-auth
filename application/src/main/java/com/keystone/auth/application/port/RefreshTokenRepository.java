package com.keystone.auth.application.port;

import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.TokenHash;
import java.util.Optional;

/**
 * Persistence port for refresh tokens. The rotation + reuse-detection contract from ADR-0005 lives
 * in the {@link RefreshToken} aggregate; this interface just exposes the state-mutation primitives
 * the use cases need.
 */
public interface RefreshTokenRepository {

  void save(RefreshToken token);

  Optional<RefreshToken> findByHash(TokenHash hash);

  /**
   * Mark a single token as used. Used during rotation when reuse has <em>not</em> been detected.
   */
  void markUsed(RefreshToken token);

  /**
   * Revoke every token in the given family. Triggered when a previously-used token is presented
   * again on {@code /refresh}.
   */
  void revokeFamily(FamilyId familyId);
}
