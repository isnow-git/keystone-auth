package com.keystone.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.keystone.auth.application.support.FakeAccessTokenIssuer;
import com.keystone.auth.application.support.FakePasswordHasher;
import com.keystone.auth.application.support.FakeRefreshTokenGenerator;
import com.keystone.auth.application.support.InMemoryRefreshTokenRepository;
import com.keystone.auth.application.support.InMemoryUserRepository;
import com.keystone.auth.application.support.Sha256Hasher;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);
  private static final String EMAIL = "alice@example.com";
  private static final String PASSWORD = "correct-horse-battery-staple";

  private InMemoryUserRepository users;
  private InMemoryRefreshTokenRepository refreshTokens;
  private FakePasswordHasher passwordHasher;
  private FakeAccessTokenIssuer accessTokenIssuer;
  private FakeRefreshTokenGenerator refreshTokenGenerator;
  private Sha256Hasher tokenHasher;
  private LoginUseCase useCase;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    refreshTokens = new InMemoryRefreshTokenRepository();
    passwordHasher = new FakePasswordHasher();
    accessTokenIssuer = new FakeAccessTokenIssuer(CLOCK, ACCESS_TTL);
    refreshTokenGenerator = new FakeRefreshTokenGenerator();
    tokenHasher = new Sha256Hasher();
    useCase =
        new LoginUseCase(
            users,
            passwordHasher,
            accessTokenIssuer,
            refreshTokenGenerator,
            tokenHasher,
            refreshTokens,
            CLOCK,
            REFRESH_TTL);

    // Pre-populate Alice.
    HashedPassword stored = passwordHasher.hash(PASSWORD);
    users.insertIfAbsent(User.register(Email.of(EMAIL), stored, NOW));
  }

  @Test
  void rejectsNullCollaborators() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new LoginUseCase(
                    null,
                    passwordHasher,
                    accessTokenIssuer,
                    refreshTokenGenerator,
                    tokenHasher,
                    refreshTokens,
                    CLOCK,
                    REFRESH_TTL));
  }

  @Test
  void rejectsNonPositiveRefreshTtl() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new LoginUseCase(
                    users,
                    passwordHasher,
                    accessTokenIssuer,
                    refreshTokenGenerator,
                    tokenHasher,
                    refreshTokens,
                    CLOCK,
                    Duration.ZERO));
  }

  @Test
  void happyPathMintsAccessAndRefreshTokens() {
    var result = useCase.execute(EMAIL, PASSWORD);

    assertThat(result).isInstanceOf(LoginResult.Success.class);
    var success = (LoginResult.Success) result;

    assertThat(success.tokens().access().value()).startsWith("fake-access-1-");
    assertThat(success.tokens().access().expiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));
    assertThat(success.tokens().refresh().value()).isEqualTo("refresh-plaintext-1");
    assertThat(success.tokens().refresh().expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));

    assertThat(refreshTokens.size()).isOne();
  }

  @Test
  void persistedRefreshTokenStoresHashNotPlaintext() {
    useCase.execute(EMAIL, PASSWORD);

    var stored = refreshTokens.findByHash(tokenHasher.sha256("refresh-plaintext-1")).orElseThrow();
    assertThat(stored.tokenHash().value()).hasSize(64);
    assertThat(stored.used()).isFalse();
    assertThat(stored.revoked()).isFalse();
    // The plaintext value is not on the persisted entity at all — only the hash is.
  }

  @Test
  void unknownEmailReturnsInvalidCredentials() {
    var result = useCase.execute("ghost@example.com", PASSWORD);

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    assertThat(refreshTokens.size()).isZero();
  }

  @Test
  void wrongPasswordReturnsInvalidCredentials() {
    var result = useCase.execute(EMAIL, "something-else-entirely");

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    assertThat(refreshTokens.size()).isZero();
  }

  @Test
  void malformedEmailReturnsInvalidCredentials() {
    var result = useCase.execute("not-an-email", PASSWORD);

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    assertThat(refreshTokens.size()).isZero();
  }

  @Test
  void unknownEmailAndWrongPasswordAreIndistinguishableToTheCaller() {
    var unknown = useCase.execute("ghost@example.com", PASSWORD);
    var wrong = useCase.execute(EMAIL, "something-else-entirely");

    assertThat(unknown.getClass()).isEqualTo(wrong.getClass());
    // Records with no components — nothing for the adapter to leak.
    assertThat(((Record) unknown).getClass().getRecordComponents()).isEmpty();
  }

  @Test
  void eachLoginIssuesADistinctRefreshFamily() {
    useCase.execute(EMAIL, PASSWORD);
    useCase.execute(EMAIL, PASSWORD);

    assertThat(refreshTokens.size()).isEqualTo(2);
    var first = refreshTokens.findByHash(tokenHasher.sha256("refresh-plaintext-1")).orElseThrow();
    var second = refreshTokens.findByHash(tokenHasher.sha256("refresh-plaintext-2")).orElseThrow();
    assertThat(first.familyId()).isNotEqualTo(second.familyId());
  }
}
