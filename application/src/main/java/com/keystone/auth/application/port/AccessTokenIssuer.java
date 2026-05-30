package com.keystone.auth.application.port;

import com.keystone.auth.domain.model.AccessToken;
import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.UserId;
import java.util.Set;

/**
 * Mints signed access tokens. Production adapter signs RS256 via Nimbus JOSE (see ADR-0001). The
 * domain treats the result as opaque.
 */
public interface AccessTokenIssuer {

  AccessToken issue(UserId subject, Set<Role> roles);
}
