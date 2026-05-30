package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final UserId USER_ID = UserId.random();
  private static final TokenHash HASH_A = new TokenHash("a".repeat(64));
  private static final TokenHash HASH_B = new TokenHash("b".repeat(64));
  private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
  private static final Duration TTL = Duration.ofDays(7);

  @Test
  void issueInitialAssignsFreshFamilyAndExpiry() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);

    assertThat(token.userId()).isEqualTo(USER_ID);
    assertThat(token.tokenHash()).isEqualTo(HASH_A);
    assertThat(token.familyId()).isNotNull();
    assertThat(token.expiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(token.used()).isFalse();
    assertThat(token.revoked()).isFalse();
    assertThat(token.isActive(NOW)).isTrue();
  }

  @Test
  void issueInitialRejectsNonPositiveTtl() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RefreshToken.issueInitial(USER_ID, HASH_A, NOW, Duration.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RefreshToken.issueInitial(USER_ID, HASH_A, NOW, Duration.ofSeconds(-1)));
  }

  @Test
  void rotateHappyPathProducesRotatedResult() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    var later = NOW.plusSeconds(60);

    var result = token.rotate(HASH_B, later, TTL);

    assertThat(result).isInstanceOf(RotationResult.Rotated.class);
    var rotated = (RotationResult.Rotated) result;

    assertThat(rotated.consumed().used()).isTrue();
    assertThat(rotated.consumed().id()).isEqualTo(token.id());
    assertThat(rotated.issued().tokenHash()).isEqualTo(HASH_B);
    assertThat(rotated.issued().familyId()).isEqualTo(token.familyId());
    assertThat(rotated.issued().id()).isNotEqualTo(token.id());
    assertThat(rotated.issued().expiresAt()).isEqualTo(later.plus(TTL));
  }

  @Test
  void rotatingAnAlreadyUsedTokenSignalsReuse() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    var rotated = (RotationResult.Rotated) token.rotate(HASH_B, NOW.plusSeconds(1), TTL);
    var alreadyUsed = rotated.consumed();

    var second = alreadyUsed.rotate(HASH_B, NOW.plusSeconds(2), TTL);

    assertThat(second).isInstanceOf(RotationResult.ReuseDetected.class);
    var reuse = (RotationResult.ReuseDetected) second;
    assertThat(reuse.compromisedFamily()).isEqualTo(token.familyId());
  }

  @Test
  void rotatingARevokedTokenIsRejected() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL).revoke();

    var result = token.rotate(HASH_B, NOW.plusSeconds(1), TTL);

    assertThat(result).isInstanceOf(RotationResult.Revoked.class);
  }

  @Test
  void revokedPrecedenceOverUsed() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    var rotated = (RotationResult.Rotated) token.rotate(HASH_B, NOW.plusSeconds(1), TTL);
    var usedAndRevoked = rotated.consumed().revoke();

    var result = usedAndRevoked.rotate(HASH_B, NOW.plusSeconds(2), TTL);

    assertThat(result).isInstanceOf(RotationResult.Revoked.class);
  }

  @Test
  void rotatingAnExpiredTokenSignalsExpired() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, Duration.ofMinutes(1));

    var result = token.rotate(HASH_B, NOW.plus(Duration.ofMinutes(2)), TTL);

    assertThat(result).isInstanceOf(RotationResult.Expired.class);
  }

  @Test
  void rotateRejectsNonPositiveTtl() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> token.rotate(HASH_B, NOW.plusSeconds(1), Duration.ZERO));
  }

  @Test
  void revokeIsIdempotent() {
    var token = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL).revoke();
    assertThat(token.revoke()).isSameAs(token);
  }

  @Test
  void isActiveReflectsState() {
    var fresh = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, Duration.ofMinutes(1));
    assertThat(fresh.isActive(NOW)).isTrue();

    assertThat(fresh.isActive(NOW.plus(Duration.ofMinutes(2)))).isFalse();
    assertThat(fresh.revoke().isActive(NOW)).isFalse();

    var rotated = (RotationResult.Rotated) fresh.rotate(HASH_B, NOW, Duration.ofMinutes(1));
    assertThat(rotated.consumed().isActive(NOW)).isFalse(); // used
  }

  @Test
  void rehydratedTokenRejectsBackwardsExpiry() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                RefreshToken.rehydrate(
                    RefreshTokenId.random(),
                    USER_ID,
                    HASH_A,
                    FamilyId.random(),
                    NOW,
                    NOW,
                    false,
                    false));
  }

  @Test
  void equalityIsByIdentity() {
    var a = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    var b = RefreshToken.issueInitial(USER_ID, HASH_A, NOW, TTL);
    var revokedA = a.revoke();

    assertThat(a).isEqualTo(revokedA);
    assertThat(a).hasSameHashCodeAs(revokedA);
    assertThat(a).isNotEqualTo(b);
  }
}
