package com.keystone.auth.infrastructure.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.TokenHash;
import com.keystone.auth.domain.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JooqTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({JooqRefreshTokenRepository.class, JooqUserRepository.class})
@Testcontainers
@EnabledIf("dockerAvailable")
class JooqRefreshTokenRepositoryIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final HashedPassword PASSWORD =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$AAA$BBB");
  private static final Duration TTL = Duration.ofDays(7);

  @SuppressWarnings("unused")
  static boolean dockerAvailable() {
    try {
      return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Autowired private JooqRefreshTokenRepository refreshTokens;
  @Autowired private JooqUserRepository users;

  private User user;
  private Instant now;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-01-01T00:00:00Z");
    user = User.register(Email.of("rt-owner@example.com"), PASSWORD, now);
    users.insertIfAbsent(user);
  }

  private TokenHash hash(String seed) {
    // Deterministic 64-char lowercase hex derived from the seed.
    var bytes = new byte[32];
    var seedBytes = seed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = seedBytes[i % seedBytes.length];
    }
    return new TokenHash(HexFormat.of().formatHex(bytes));
  }

  @Test
  void saveAndFindByHashRoundTrips() {
    var token = RefreshToken.issueInitial(user.id(), hash("rt-1"), now, TTL);

    refreshTokens.save(token);
    var loaded = refreshTokens.findByHash(token.tokenHash()).orElseThrow();

    assertThat(loaded.id()).isEqualTo(token.id());
    assertThat(loaded.userId()).isEqualTo(user.id());
    assertThat(loaded.familyId()).isEqualTo(token.familyId());
    assertThat(loaded.used()).isFalse();
    assertThat(loaded.revoked()).isFalse();
  }

  @Test
  void markUsedFlipsTheUsedFlag() {
    var token = RefreshToken.issueInitial(user.id(), hash("rt-2"), now, TTL);
    refreshTokens.save(token);

    refreshTokens.markUsed(token);

    assertThat(refreshTokens.findByHash(token.tokenHash()).orElseThrow().used()).isTrue();
  }

  @Test
  void revokeFamilyMarksEveryMemberRevoked() {
    var first = RefreshToken.issueInitial(user.id(), hash("fam-1"), now, TTL);
    var second =
        RefreshToken.rehydrate(
            com.keystone.auth.domain.model.RefreshTokenId.random(),
            user.id(),
            hash("fam-2"),
            first.familyId(),
            now,
            now.plus(TTL),
            false,
            false);
    var other = RefreshToken.issueInitial(user.id(), hash("other"), now, TTL);
    refreshTokens.save(first);
    refreshTokens.save(second);
    refreshTokens.save(other);

    refreshTokens.revokeFamily(first.familyId());

    assertThat(refreshTokens.findByHash(first.tokenHash()).orElseThrow().revoked()).isTrue();
    assertThat(refreshTokens.findByHash(second.tokenHash()).orElseThrow().revoked()).isTrue();
    assertThat(refreshTokens.findByHash(other.tokenHash()).orElseThrow().revoked()).isFalse();
  }

  @Test
  void findByHashReturnsEmptyForUnknown() {
    assertThat(refreshTokens.findByHash(hash("nothing-here"))).isEmpty();
  }
}
