package com.keystone.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenPairTest {

  @Test
  void rejectsNullParts() {
    var access = new AccessToken("jwt", Instant.now().plusSeconds(60));
    var refresh =
        new RefreshTokenValue("v", new TokenHash("a".repeat(64)), Instant.now().plusSeconds(120));

    assertThatNullPointerException().isThrownBy(() -> new TokenPair(null, refresh));
    assertThatNullPointerException().isThrownBy(() -> new TokenPair(access, null));
  }

  @Test
  void bundlesBothTokens() {
    var access = new AccessToken("jwt", Instant.now().plusSeconds(60));
    var refresh =
        new RefreshTokenValue("v", new TokenHash("a".repeat(64)), Instant.now().plusSeconds(120));

    var pair = new TokenPair(access, refresh);

    assertThat(pair.access()).isSameAs(access);
    assertThat(pair.refresh()).isSameAs(refresh);
  }
}
