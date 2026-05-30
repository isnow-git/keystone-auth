package com.keystone.auth.application.support;

import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.RefreshToken;
import com.keystone.auth.domain.model.RefreshTokenId;
import com.keystone.auth.domain.model.TokenHash;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory fake for use-case tests. */
public final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

  private final Map<RefreshTokenId, RefreshToken> store = new LinkedHashMap<>();

  @Override
  public void save(RefreshToken token) {
    store.put(token.id(), token);
  }

  @Override
  public Optional<RefreshToken> findByHash(TokenHash hash) {
    return store.values().stream().filter(t -> t.tokenHash().equals(hash)).findFirst();
  }

  @Override
  public void markUsed(RefreshToken token) {
    store.put(token.id(), token);
  }

  @Override
  public void revokeFamily(FamilyId familyId) {
    store.replaceAll((id, token) -> token.familyId().equals(familyId) ? token.revoke() : token);
  }

  public int size() {
    return store.size();
  }

  public Optional<RefreshToken> findById(RefreshTokenId id) {
    return Optional.ofNullable(store.get(id));
  }
}
