package com.keystone.auth.infrastructure.persistence.jooq;

import static com.keystone.auth.infrastructure.persistence.jooq.generated.Tables.USERS;

import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.domain.model.UserId;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.IntegrityConstraintViolationException;

/** jOOQ-backed {@link UserRepository}. The hot path is {@link #findByEmail} on {@code /login}. */
public final class JooqUserRepository implements UserRepository {

  private final DSLContext dsl;

  public JooqUserRepository(DSLContext dsl) {
    this.dsl = Objects.requireNonNull(dsl, "dsl");
  }

  @Override
  public boolean insertIfAbsent(User user) {
    var record = UserRecordMapper.toRecord(user);
    try {
      return dsl.insertInto(USERS).set(record).onConflictDoNothing().execute() == 1;
    } catch (IntegrityConstraintViolationException e) {
      // Defensive: the unique constraint should be caught by onConflictDoNothing on Postgres, but
      // a non-Postgres dialect (or a future column with its own UNIQUE) could still raise.
      return false;
    }
  }

  @Override
  public Optional<User> findById(UserId id) {
    return Optional.ofNullable(dsl.selectFrom(USERS).where(USERS.ID.eq(id.value())).fetchOne())
        .map(UserRecordMapper::toDomain);
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return Optional.ofNullable(
            dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email.value())).fetchOne())
        .map(UserRecordMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return dsl.fetchExists(dsl.selectOne().from(USERS).where(USERS.EMAIL.eq(email.value())));
  }
}
