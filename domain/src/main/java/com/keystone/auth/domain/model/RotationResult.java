package com.keystone.auth.domain.model;

/**
 * Outcome of attempting to rotate a refresh token. Sealed so that callers ({@code RefreshUseCase})
 * exhaustively handle every branch — exceptions are reserved for genuine bugs, not control flow.
 */
public sealed interface RotationResult {

  /**
   * Successful rotation. The {@code consumed} token is the presented one with {@code used=true};
   * persistence must update it. The {@code issued} token is brand-new and belongs to the same
   * family.
   */
  record Rotated(RefreshToken consumed, RefreshToken issued) implements RotationResult {}

  /**
   * The presented token was already marked {@code used}. Treat as theft: the caller must revoke
   * every token in {@link #compromisedFamily()}.
   */
  record ReuseDetected(FamilyId compromisedFamily) implements RotationResult {}

  /** The token's TTL has elapsed. Reject. */
  record Expired() implements RotationResult {}

  /** The token (or its family) was previously revoked. Reject. */
  record Revoked() implements RotationResult {}
}
