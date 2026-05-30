package com.keystone.auth.application.support;

import com.keystone.auth.application.port.AccessTokenIssuer;
import com.keystone.auth.domain.model.AccessToken;
import com.keystone.auth.domain.model.Role;
import com.keystone.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Issues deterministic non-cryptographic tokens for use-case tests. */
public final class FakeAccessTokenIssuer implements AccessTokenIssuer {

  private final Clock clock;
  private final Duration ttl;
  private final AtomicInteger sequence = new AtomicInteger();

  public FakeAccessTokenIssuer(Clock clock, Duration ttl) {
    this.clock = clock;
    this.ttl = ttl;
  }

  @Override
  public AccessToken issue(UserId subject, Set<Role> roles) {
    var serial = sequence.incrementAndGet();
    var token = "fake-access-" + serial + "-" + subject.value();
    return new AccessToken(token, clock.instant().plus(ttl));
  }
}
