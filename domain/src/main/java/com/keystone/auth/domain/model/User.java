package com.keystone.auth.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Authenticated principal. Immutable; state changes (password change, role grant) return a new
 * instance so callers can decide whether to persist them.
 */
public final class User {

  private final UserId id;
  private final Email email;
  private final HashedPassword passwordHash;
  private final Set<Role> roles;
  private final Instant createdAt;
  private final Instant updatedAt;

  private User(
      UserId id,
      Email email,
      HashedPassword passwordHash,
      Set<Role> roles,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.email = Objects.requireNonNull(email, "email");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    Objects.requireNonNull(roles, "roles");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("a user must have at least one role");
    }
    this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not precede createdAt");
    }
  }

  /**
   * Mint a brand-new user with the default {@link Role#USER USER} authority.
   *
   * @param email validated email address (must be unique — enforced at the repository boundary)
   * @param passwordHash already-encoded password
   * @param now wall-clock time used to stamp {@code createdAt} and {@code updatedAt}
   */
  public static User register(Email email, HashedPassword passwordHash, Instant now) {
    return new User(UserId.random(), email, passwordHash, Set.of(Role.USER), now, now);
  }

  /** Rehydrate a user from the repository. */
  public static User rehydrate(
      UserId id,
      Email email,
      HashedPassword passwordHash,
      Set<Role> roles,
      Instant createdAt,
      Instant updatedAt) {
    return new User(id, email, passwordHash, roles, createdAt, updatedAt);
  }

  public User changePassword(HashedPassword newHash, Instant now) {
    Objects.requireNonNull(newHash, "newHash");
    Objects.requireNonNull(now, "now");
    if (newHash.equals(this.passwordHash)) {
      throw new IllegalArgumentException("new password hash must differ from the current one");
    }
    return new User(id, email, newHash, roles, createdAt, now);
  }

  public User grantRole(Role role, Instant now) {
    Objects.requireNonNull(role, "role");
    if (roles.contains(role)) {
      return this;
    }
    var updated = new LinkedHashSet<>(roles);
    updated.add(role);
    return new User(id, email, passwordHash, updated, createdAt, now);
  }

  public User revokeRole(Role role, Instant now) {
    Objects.requireNonNull(role, "role");
    if (!roles.contains(role)) {
      return this;
    }
    if (roles.size() == 1) {
      throw new IllegalStateException("cannot revoke the last role from a user");
    }
    var updated = new LinkedHashSet<>(roles);
    updated.remove(role);
    return new User(id, email, passwordHash, updated, createdAt, now);
  }

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public UserId id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public HashedPassword passwordHash() {
    return passwordHash;
  }

  public Set<Role> roles() {
    return roles;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof User other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "User[id=" + id.value() + ", email=" + email.value() + ", roles=" + roles + "]";
  }
}
