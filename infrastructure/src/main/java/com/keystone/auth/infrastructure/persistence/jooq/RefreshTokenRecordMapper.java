package com.keystone.auth.infrastructure.persistence.jooq;

import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.RefreshTokenId;
import com.keystone.auth.domain.model.TokenHash;
import com.keystone.auth.domain.model.UserId;
import com.keystone.auth.infrastructure.persistence.jooq.generated.tables.records.RefreshTokensRecord;

/** Bidirectional mapping between the {@link RefreshToken} aggregate and the jOOQ record. */
final class RefreshTokenRecordMapper {

  private RefreshTokenRecordMapper() {}

  static RefreshTokensRecord toRecord(RefreshToken token) {
    var record = new RefreshTokensRecord();
    record.setId(token.id().value());
    record.setUserId(token.userId().value());
    record.setTokenHash(token.tokenHash().value());
    record.setFamilyId(token.familyId().value());
    record.setCreatedAt(token.createdAt());
    record.setExpiresAt(token.expiresAt());
    record.setUsed(token.used());
    record.setRevoked(token.revoked());
    return record;
  }

  static RefreshToken toDomain(RefreshTokensRecord record) {
    return RefreshToken.rehydrate(
        new RefreshTokenId(record.getId()),
        UserId.of(record.getUserId()),
        new TokenHash(record.getTokenHash()),
        new FamilyId(record.getFamilyId()),
        record.getCreatedAt(),
        record.getExpiresAt(),
        Boolean.TRUE.equals(record.getUsed()),
        Boolean.TRUE.equals(record.getRevoked()));
  }
}
