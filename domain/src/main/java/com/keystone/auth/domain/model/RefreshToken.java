package com.keystone.auth.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Persisted refresh-token state. The domain holds the rotation + reuse-detection rules; the
 * cryptographic generation of the plaintext value belongs to the application layer (via a generator
 * port).
 */
public final class RefreshToken {

  private final RefreshTokenId id;
  private final UserId userId;
  private final TokenHash tokenHash;
  private final FamilyId familyId;
  private final Instant createdAt;
  private final Instant expiresAt;
  private final boolean used;
  private final boolean revoked;

  private RefreshToken(
      RefreshTokenId id,
      UserId userId,
      TokenHash tokenHash,
      FamilyId familyId,
      Instant createdAt,
      Instant expiresAt,
      boolean used,
      boolean revoked) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    this.familyId = Objects.requireNonNull(familyId, "familyId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be strictly after createdAt");
    }
    this.used = used;
    this.revoked = revoked;
  }

  /** First token of a new family — issued on {@code /login}. */
  public static RefreshToken issueInitial(
      UserId userId, TokenHash tokenHash, Instant now, Duration ttl) {
    Objects.requireNonNull(ttl, "ttl");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    return new RefreshToken(
        RefreshTokenId.random(),
        userId,
        tokenHash,
        FamilyId.random(),
        now,
        now.plus(ttl),
        false,
        false);
  }

  /** Rehydrate a token from persistence. */
  public static RefreshToken rehydrate(
      RefreshTokenId id,
      UserId userId,
      TokenHash tokenHash,
      FamilyId familyId,
      Instant createdAt,
      Instant expiresAt,
      boolean used,
      boolean revoked) {
    return new RefreshToken(id, userId, tokenHash, familyId, createdAt, expiresAt, used, revoked);
  }

  /**
   * Apply the rotation protocol described in ADR-0005.
   *
   * <p>The caller (the {@code RefreshUseCase}) is responsible for persisting whichever side of the
   * sealed {@link RotationResult} comes back: updating the consumed token, inserting the new one,
   * or revoking the entire family on reuse.
   *
   * @param nextHash hash of the freshly-generated plaintext token
   * @param now wall-clock time, supplied by the caller (keeps the domain deterministic in tests)
   * @param ttl lifetime of the new token
   */
  public RotationResult rotate(TokenHash nextHash, Instant now, Duration ttl) {
    Objects.requireNonNull(nextHash, "nextHash");
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(ttl, "ttl");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    if (revoked) {
      return new RotationResult.Revoked();
    }
    if (used) {
      return new RotationResult.ReuseDetected(familyId);
    }
    if (!now.isBefore(expiresAt)) {
      return new RotationResult.Expired();
    }

    var consumed = withFlags(true, this.revoked);
    var issued =
        new RefreshToken(
            RefreshTokenId.random(), userId, nextHash, familyId, now, now.plus(ttl), false, false);
    return new RotationResult.Rotated(consumed, issued);
  }

  /** Revoke this token only. Family-wide revocation is the repository's job. */
  public RefreshToken revoke() {
    if (revoked) {
      return this;
    }
    return withFlags(this.used, true);
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isActive(Instant now) {
    return !used && !revoked && !isExpired(now);
  }

  private RefreshToken withFlags(boolean used, boolean revoked) {
    return new RefreshToken(id, userId, tokenHash, familyId, createdAt, expiresAt, used, revoked);
  }

  public RefreshTokenId id() {
    return id;
  }

  public UserId userId() {
    return userId;
  }

  public TokenHash tokenHash() {
    return tokenHash;
  }

  public FamilyId familyId() {
    return familyId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public boolean used() {
    return used;
  }

  public boolean revoked() {
    return revoked;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RefreshToken other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
