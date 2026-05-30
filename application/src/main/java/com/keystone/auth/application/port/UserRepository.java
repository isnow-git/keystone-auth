package com.keystone.auth.application.port;

import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.domain.model.UserId;
import java.util.Optional;

/**
 * Persistence port for {@link User} aggregates. The application layer talks to this interface;
 * adapters in {@code :infrastructure} provide the implementation (jOOQ-backed in production, an
 * in-memory fake in unit tests).
 */
public interface UserRepository {

  /**
   * Insert a new user. Implementations must enforce uniqueness on the email column and surface
   * conflicts by returning {@code false} — never by throwing the underlying SQL exception.
   *
   * @return {@code true} if the user was inserted, {@code false} if the email was already taken
   */
  boolean insertIfAbsent(User user);

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(Email email);

  boolean existsByEmail(Email email);
}
