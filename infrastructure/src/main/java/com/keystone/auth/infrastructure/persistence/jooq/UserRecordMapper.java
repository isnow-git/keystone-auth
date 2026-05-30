package com.keystone.auth.infrastructure.persistence.jooq;

import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.domain.model.UserId;
import com.keystone.auth.infrastructure.persistence.jooq.generated.tables.records.UsersRecord;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Bidirectional mapping between the {@link User} aggregate and the generated jOOQ {@code
 * UsersRecord}. Lives in {@code :infrastructure} so the domain stays framework-free.
 */
final class UserRecordMapper {

  private UserRecordMapper() {}

  static UsersRecord toRecord(User user) {
    var record = new UsersRecord();
    record.setId(user.id().value());
    record.setEmail(user.email().value());
    record.setPasswordHash(user.passwordHash().encoded());
    record.setRoles(user.roles().stream().map(Role::name).toArray(String[]::new));
    record.setCreatedAt(user.createdAt());
    record.setUpdatedAt(user.updatedAt());
    return record;
  }

  static User toDomain(UsersRecord record) {
    var roles =
        Arrays.stream(record.getRoles())
            .map(Role::new)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return User.rehydrate(
        UserId.of(record.getId()),
        Email.of(record.getEmail()),
        new HashedPassword(record.getPasswordHash()),
        roles,
        record.getCreatedAt(),
        record.getUpdatedAt());
  }
}
