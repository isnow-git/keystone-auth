package com.keystone.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.keystone.auth.application.support.InMemoryRefreshTokenRepository;
import com.keystone.auth.application.support.Sha256Hasher;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogoutUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration TTL = Duration.ofDays(7);

  private InMemoryRefreshTokenRepository refreshTokens;
  private Sha256Hasher tokenHasher;
  private LogoutUseCase useCase;
  private UserId userId;

  @BeforeEach
  void setUp() {
    refreshTokens = new InMemoryRefreshTokenRepository();
    tokenHasher = new Sha256Hasher();
    useCase = new LogoutUseCase(refreshTokens, tokenHasher);
    userId = UserId.random();
  }

  @Test
  void rejectsNullCollaborators() {
    assertThatNullPointerException().isThrownBy(() -> new LogoutUseCase(null, tokenHasher));
    assertThatNullPointerException().isThrownBy(() -> new LogoutUseCase(refreshTokens, null));
  }

  @Test
  void revokesTheEntireFamilyForAKnownToken() {
    var hashA = tokenHasher.sha256("rt-a");
    var first = RefreshToken.issueInitial(userId, hashA, NOW, TTL);
    var second =
        RefreshToken.rehydrate(
            com.keystone.auth.domain.model.RefreshTokenId.random(),
            userId,
            tokenHasher.sha256("rt-b"),
            first.familyId(),
            NOW,
            NOW.plus(TTL),
            false,
            false);
    refreshTokens.save(first);
    refreshTokens.save(second);

    useCase.execute("rt-a");

    assertThat(refreshTokens.findById(first.id()).orElseThrow().revoked()).isTrue();
    assertThat(refreshTokens.findByHash(second.tokenHash()).orElseThrow().revoked()).isTrue();
  }

  @Test
  void leavesOtherFamiliesAlone() {
    var familyA = RefreshToken.issueInitial(userId, tokenHasher.sha256("rt-fam-a"), NOW, TTL);
    var familyB = RefreshToken.issueInitial(userId, tokenHasher.sha256("rt-fam-b"), NOW, TTL);
    refreshTokens.save(familyA);
    refreshTokens.save(familyB);

    useCase.execute("rt-fam-a");

    assertThat(refreshTokens.findById(familyA.id()).orElseThrow().revoked()).isTrue();
    assertThat(refreshTokens.findById(familyB.id()).orElseThrow().revoked()).isFalse();
  }

  @Test
  void unknownTokenIsASilentNoOp() {
    assertThatCode(() -> useCase.execute("never-issued")).doesNotThrowAnyException();
    assertThat(refreshTokens.size()).isZero();
  }

  @Test
  void nullPresentedPlaintextIsASilentNoOp() {
    var token = RefreshToken.issueInitial(userId, tokenHasher.sha256("rt-x"), NOW, TTL);
    refreshTokens.save(token);

    assertThatCode(() -> useCase.execute(null)).doesNotThrowAnyException();

    // Existing token untouched.
    assertThat(refreshTokens.findById(token.id()).orElseThrow().revoked()).isFalse();
  }

  @Test
  void isIdempotentAcrossSuccessiveCalls() {
    var token = RefreshToken.issueInitial(userId, tokenHasher.sha256("rt-once"), NOW, TTL);
    refreshTokens.save(token);

    useCase.execute("rt-once");
    assertThatCode(() -> useCase.execute("rt-once")).doesNotThrowAnyException();

    assertThat(refreshTokens.findById(token.id()).orElseThrow().revoked()).isTrue();
  }
}
