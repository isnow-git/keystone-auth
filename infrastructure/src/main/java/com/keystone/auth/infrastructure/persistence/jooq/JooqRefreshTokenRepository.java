package com.keystone.auth.infrastructure.persistence.jooq;

import static com.keystone.auth.infrastructure.persistence.jooq.generated.Tables.REFRESH_TOKENS;

import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.TokenHash;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/** jOOQ-backed {@link RefreshTokenRepository}. */
public final class JooqRefreshTokenRepository implements RefreshTokenRepository {

  private final DSLContext dsl;

  public JooqRefreshTokenRepository(DSLContext dsl) {
    this.dsl = Objects.requireNonNull(dsl, "dsl");
  }

  @Override
  public void save(RefreshToken token) {
    dsl.insertInto(REFRESH_TOKENS).set(RefreshTokenRecordMapper.toRecord(token)).execute();
  }

  @Override
  public Optional<RefreshToken> findByHash(TokenHash hash) {
    return Optional.ofNullable(
            dsl.selectFrom(REFRESH_TOKENS)
                .where(REFRESH_TOKENS.TOKEN_HASH.eq(hash.value()))
                .fetchOne())
        .map(RefreshTokenRecordMapper::toDomain);
  }

  @Override
  public void markUsed(RefreshToken token) {
    dsl.update(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.USED, true)
        .where(REFRESH_TOKENS.ID.eq(token.id().value()))
        .execute();
  }

  @Override
  public void revokeFamily(FamilyId familyId) {
    dsl.update(REFRESH_TOKENS)
        .set(REFRESH_TOKENS.REVOKED, true)
        .where(REFRESH_TOKENS.FAMILY_ID.eq(familyId.value()))
        .execute();
  }
}
