package com.keystone.auth.application.support;

import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.domain.model.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake for use-case tests. Mirrors the real repository's uniqueness contract: {@link
 * #insertIfAbsent(User)} refuses duplicates on email.
 */
public final class InMemoryUserRepository implements UserRepository {

  private final Map<UserId, User> byId = new LinkedHashMap<>();
  private final Map<Email, UserId> byEmail = new LinkedHashMap<>();

  @Override
  public boolean insertIfAbsent(User user) {
    if (byEmail.containsKey(user.email())) {
      return false;
    }
    byId.put(user.id(), user);
    byEmail.put(user.email(), user.id());
    return true;
  }

  @Override
  public Optional<User> findById(UserId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return Optional.ofNullable(byEmail.get(email)).map(byId::get);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return byEmail.containsKey(email);
  }

  public int size() {
    return byId.size();
  }
}
