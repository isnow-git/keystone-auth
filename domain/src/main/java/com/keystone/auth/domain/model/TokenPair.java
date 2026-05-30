package com.keystone.auth.domain.model;

import java.util.Objects;

/** A freshly issued access token paired with its companion refresh token. */
public record TokenPair(AccessToken access, RefreshTokenValue refresh) {

  public TokenPair {
    Objects.requireNonNull(access, "access token must not be null");
    Objects.requireNonNull(refresh, "refresh token must not be null");
  }
}
