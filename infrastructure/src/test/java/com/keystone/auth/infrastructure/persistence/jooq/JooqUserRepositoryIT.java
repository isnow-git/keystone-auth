package com.keystone.auth.infrastructure.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.User;
import java.time.Instant;
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
@Import(JooqUserRepository.class)
@Testcontainers
@EnabledIf("dockerAvailable")
class JooqUserRepositoryIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  /** JUnit 5 condition: skip the whole class when no Docker daemon is reachable. */
  @SuppressWarnings("unused")
  static boolean dockerAvailable() {
    try {
      return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static final HashedPassword HASH =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$AAA$BBB");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private JooqUserRepository repository;

  @Test
  void insertIfAbsentReturnsTrueOnFirstInsert() {
    var user = User.register(Email.of("first@example.com"), HASH, NOW);

    assertThat(repository.insertIfAbsent(user)).isTrue();
    assertThat(repository.existsByEmail(user.email())).isTrue();
  }

  @Test
  void insertIfAbsentReturnsFalseOnDuplicateEmail() {
    var first = User.register(Email.of("dup@example.com"), HASH, NOW);
    var second = User.register(Email.of("dup@example.com"), HASH, NOW);

    assertThat(repository.insertIfAbsent(first)).isTrue();
    assertThat(repository.insertIfAbsent(second)).isFalse();
  }

  @Test
  void findByEmailRoundTripsAllFields() {
    var original =
        User.register(Email.of("alice@example.com"), HASH, NOW).grantRole(Role.ADMIN, NOW);
    repository.insertIfAbsent(original);

    var loaded = repository.findByEmail(original.email()).orElseThrow();

    assertThat(loaded.id()).isEqualTo(original.id());
    assertThat(loaded.email()).isEqualTo(original.email());
    assertThat(loaded.passwordHash()).isEqualTo(original.passwordHash());
    assertThat(loaded.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
  }

  @Test
  void findByIdReturnsEmptyForUnknownId() {
    assertThat(repository.findById(com.keystone.auth.domain.model.UserId.random())).isEmpty();
  }

  @Test
  void existsByEmailReturnsFalseForUnknown() {
    assertThat(repository.existsByEmail(Email.of("ghost@example.com"))).isFalse();
  }
}
