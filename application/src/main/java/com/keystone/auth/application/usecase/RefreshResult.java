package com.keystone.auth.application.usecase;

import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.TokenPair;

/**
 * Outcome of {@link RefreshUseCase#execute}. Sealed so the REST adapter handles every branch
 * exhaustively.
 *
 * <p>The REST adapter is expected to collapse every failure variant to a single response ({@code
 * 401 Unauthorized} with an opaque body) so the client cannot tell <em>why</em> the refresh failed
 * — leaking that information would help attackers distinguish "token expired" (benign) from "reuse
 * detected" (theft suspected). The variants exist so the use case can log structurally.
 */
public sealed interface RefreshResult {

  record Success(TokenPair tokens) implements RefreshResult {}

  /** The presented token hash was not in the repository. */
  record TokenNotFound() implements RefreshResult {}

  /** The token's TTL has elapsed. */
  record Expired() implements RefreshResult {}

  /** The token (or its family) was previously revoked. */
  record Revoked() implements RefreshResult {}

  /** A used token was presented again. The entire family has been revoked. */
  record ReuseDetected(FamilyId family) implements RefreshResult {}
}
