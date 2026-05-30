package com.keystone.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.keystone.auth.application.support.FakeAccessTokenIssuer;
import com.keystone.auth.application.support.FakeRefreshTokenGenerator;
import com.keystone.auth.application.support.InMemoryRefreshTokenRepository;
import com.keystone.auth.application.support.InMemoryUserRepository;
import com.keystone.auth.application.support.Sha256Hasher;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.RefreshTokenId;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);
  private static final HashedPassword PASSWORD_HASH =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$AAA$BBB");

  private InMemoryUserRepository users;
  private InMemoryRefreshTokenRepository refreshTokens;
  private Sha256Hasher tokenHasher;
  private FakeRefreshTokenGenerator generator;
  private FakeAccessTokenIssuer issuer;
  private RefreshUseCase useCase;
  private User alice;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    refreshTokens = new InMemoryRefreshTokenRepository();
    tokenHasher = new Sha256Hasher();
    generator = new FakeRefreshTokenGenerator();
    issuer = new FakeAccessTokenIssuer(CLOCK, ACCESS_TTL);
    useCase =
        new RefreshUseCase(
            refreshTokens, tokenHasher, generator, issuer, users, CLOCK, REFRESH_TTL);

    alice = User.register(Email.of("alice@example.com"), PASSWORD_HASH, NOW);
    users.insertIfAbsent(alice);
  }

  /** Helper: persist a fresh refresh token whose hash matches the supplied plaintext. */
  private RefreshToken seedRefreshToken(String plaintext) {
    var hash = tokenHasher.sha256(plaintext);
    var token = RefreshToken.issueInitial(alice.id(), hash, NOW, REFRESH_TTL);
    refreshTokens.save(token);
    return token;
  }

  @Test
  void happyPathRotatesAndIssuesANewPair() {
    var current = seedRefreshToken("current-plaintext");

    var result = useCase.execute("current-plaintext");

    assertThat(result).isInstanceOf(RefreshResult.Success.class);
    var success = (RefreshResult.Success) result;

    // New access token issued.
    assertThat(success.tokens().access().value()).startsWith("fake-access-1-");
    assertThat(success.tokens().access().expiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));

    // New refresh token plaintext returned to the client.
    assertThat(success.tokens().refresh().value()).isEqualTo("refresh-plaintext-1");
    assertThat(success.tokens().refresh().expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));

    // Two persisted tokens: the old one marked used, the new one fresh.
    var oldStored = refreshTokens.findById(current.id()).orElseThrow();
    assertThat(oldStored.used()).isTrue();

    var newStored = refreshTokens.findByHash(success.tokens().refresh().hash()).orElseThrow();
    assertThat(newStored.familyId()).isEqualTo(current.familyId());
    assertThat(newStored.used()).isFalse();
    assertThat(newStored.revoked()).isFalse();
  }

  @Test
  void unknownTokenYieldsTokenNotFound() {
    var result = useCase.execute("never-issued");

    assertThat(result).isInstanceOf(RefreshResult.TokenNotFound.class);
    assertThat(refreshTokens.size()).isZero();
  }

  @Test
  void expiredTokenYieldsExpired() {
    var pastIssued =
        RefreshToken.issueInitial(
            alice.id(),
            tokenHasher.sha256("past"),
            NOW.minus(Duration.ofDays(8)),
            Duration.ofDays(1));
    refreshTokens.save(pastIssued);

    var result = useCase.execute("past");

    assertThat(result).isInstanceOf(RefreshResult.Expired.class);
  }

  @Test
  void revokedTokenYieldsRevoked() {
    var token = seedRefreshToken("revoked-one");
    refreshTokens.save(token.revoke());

    var result = useCase.execute("revoked-one");

    assertThat(result).isInstanceOf(RefreshResult.Revoked.class);
  }

  @Test
  void reusedTokenRevokesTheEntireFamilyAndSignals() {
    var first = seedRefreshToken("first");
    // Rotate once — second is now active in the same family.
    useCase.execute("first");

    // The client (or attacker) presents the already-used "first" again.
    var result = useCase.execute("first");

    assertThat(result).isInstanceOf(RefreshResult.ReuseDetected.class);
    var reuse = (RefreshResult.ReuseDetected) result;
    assertThat(reuse.family()).isEqualTo(first.familyId());

    // Every token in the family is now revoked.
    var firstStored = refreshTokens.findById(first.id()).orElseThrow();
    assertThat(firstStored.revoked()).isTrue();

    var secondStored =
        refreshTokens.findByHash(tokenHasher.sha256("refresh-plaintext-1")).orElseThrow();
    assertThat(secondStored.revoked()).isTrue();
  }

  @Test
  void rotationForADeletedUserDegradesToTokenNotFound() {
    // Persist a refresh token whose user is not in the repository — simulates the race with
    // account deletion + ON DELETE CASCADE not yet applied.
    var orphanUserId = UserId.random();
    var orphanFamily = FamilyId.random();
    var orphanToken =
        RefreshToken.rehydrate(
            RefreshTokenId.random(),
            orphanUserId,
            tokenHasher.sha256("orphan"),
            orphanFamily,
            NOW,
            NOW.plus(REFRESH_TTL),
            false,
            false);
    refreshTokens.save(orphanToken);

    var result = useCase.execute("orphan");

    assertThat(result).isInstanceOf(RefreshResult.TokenNotFound.class);
  }

  @Test
  void rejectsNonPositiveTtl() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RefreshUseCase(
                    refreshTokens, tokenHasher, generator, issuer, users, CLOCK, Duration.ZERO));
  }

  @Test
  void rejectsNullPlaintext() {
    var token = seedRefreshToken("ignored");
    assertThat(token).isNotNull();
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> useCase.execute(null));
  }

  @Test
  void rotatingDoesNotChangeTheFamily() {
    var first = seedRefreshToken("first");

    useCase.execute("first");

    var second = refreshTokens.findByHash(tokenHasher.sha256("refresh-plaintext-1")).orElseThrow();
    assertThat(second.familyId()).isEqualTo(first.familyId());
  }

  @Test
  void rotationProducesADifferentTokenHash() {
    var first = seedRefreshToken("first");

    var result = useCase.execute("first");
    var success = (RefreshResult.Success) result;

    assertThat(success.tokens().refresh().hash()).isNotEqualTo(first.tokenHash());
  }

  @Test
  void presentedHashIsNotPersistedAlongsideTheNewToken() {
    seedRefreshToken("current-plaintext");

    var result = useCase.execute("current-plaintext");
    var success = (RefreshResult.Success) result;

    // The returned refresh value carries a brand new hash, not the input one.
    var presented = tokenHasher.sha256("current-plaintext");
    assertThat(success.tokens().refresh().hash()).isNotEqualTo(presented);
  }

  @Test
  void rotatingTwiceConsumesEachIssuedTokenInTurn() {
    seedRefreshToken("initial");

    var first = (RefreshResult.Success) useCase.execute("initial");
    assertThat(first.tokens().refresh().value()).isEqualTo("refresh-plaintext-1");

    var second = (RefreshResult.Success) useCase.execute("refresh-plaintext-1");
    assertThat(second.tokens().refresh().value()).isEqualTo("refresh-plaintext-2");

    // Now three rows: original (used), first rotation (used), second rotation (active).
    assertThat(refreshTokens.size()).isEqualTo(3);
  }
}
