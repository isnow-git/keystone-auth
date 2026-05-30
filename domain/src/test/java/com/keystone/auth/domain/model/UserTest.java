package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final HashedPassword HASH_A =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$AAA$BBB");
  private static final HashedPassword HASH_B =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$CCC$DDD");
  private static final Email EMAIL = Email.of("alice@example.com");
  private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

  @Test
  void registerGivesTheUserSensibleDefaults() {
    var user = User.register(EMAIL, HASH_A, NOW);

    assertThat(user.id()).isNotNull();
    assertThat(user.email()).isEqualTo(EMAIL);
    assertThat(user.passwordHash()).isEqualTo(HASH_A);
    assertThat(user.roles()).containsExactly(Role.USER);
    assertThat(user.createdAt()).isEqualTo(NOW);
    assertThat(user.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void changePasswordReturnsNewInstanceAndStampsUpdatedAt() {
    var user = User.register(EMAIL, HASH_A, NOW);
    var later = NOW.plusSeconds(60);

    var changed = user.changePassword(HASH_B, later);

    assertThat(changed.passwordHash()).isEqualTo(HASH_B);
    assertThat(changed.updatedAt()).isEqualTo(later);
    assertThat(changed.createdAt()).isEqualTo(NOW);
    assertThat(changed.id()).isEqualTo(user.id());
    assertThat(user.passwordHash()).isEqualTo(HASH_A); // original untouched
  }

  @Test
  void changePasswordRejectsSameHash() {
    var user = User.register(EMAIL, HASH_A, NOW);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> user.changePassword(HASH_A, NOW.plusSeconds(1)));
  }

  @Test
  void grantRoleIsIdempotent() {
    var user = User.register(EMAIL, HASH_A, NOW);
    var same = user.grantRole(Role.USER, NOW.plusSeconds(1));
    assertThat(same).isSameAs(user);
  }

  @Test
  void grantRoleAddsRole() {
    var user = User.register(EMAIL, HASH_A, NOW);
    var promoted = user.grantRole(Role.ADMIN, NOW.plusSeconds(1));

    assertThat(promoted.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    assertThat(promoted.hasRole(Role.ADMIN)).isTrue();
  }

  @Test
  void revokeRoleRemovesRole() {
    var user =
        User.register(EMAIL, HASH_A, NOW).grantRole(Role.ADMIN, NOW).revokeRole(Role.USER, NOW);
    assertThat(user.roles()).containsExactly(Role.ADMIN);
  }

  @Test
  void revokingLastRoleIsForbidden() {
    var user = User.register(EMAIL, HASH_A, NOW);
    assertThatThrownBy(() -> user.revokeRole(Role.USER, NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void revokeRoleNotHeldIsNoOp() {
    var user = User.register(EMAIL, HASH_A, NOW);
    assertThat(user.revokeRole(Role.ADMIN, NOW)).isSameAs(user);
  }

  @Test
  void updatedAtBeforeCreatedAtIsForbiddenOnRehydrate() {
    var id = UserId.random();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> User.rehydrate(id, EMAIL, HASH_A, Set.of(Role.USER), NOW, NOW.minusSeconds(1)));
  }

  @Test
  void emptyRolesRejected() {
    var id = UserId.random();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> User.rehydrate(id, EMAIL, HASH_A, Set.of(), NOW, NOW));
  }

  @Test
  void equalityIsByIdentity() {
    var a = User.register(EMAIL, HASH_A, NOW);
    var same = a.changePassword(HASH_B, NOW.plusSeconds(1));
    var other = User.register(EMAIL, HASH_A, NOW);

    assertThat(a).isEqualTo(same);
    assertThat(a).isNotEqualTo(other);
    assertThat(a).hasSameHashCodeAs(same);
  }
}
