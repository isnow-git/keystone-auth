package com.keystone.auth.application.support;

import com.keystone.auth.application.port.RefreshTokenGenerator;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic, monotonically increasing refresh token values for tests. */
public final class FakeRefreshTokenGenerator implements RefreshTokenGenerator {

  private final AtomicInteger sequence = new AtomicInteger();

  @Override
  public String generate() {
    return "refresh-plaintext-" + sequence.incrementAndGet();
  }
}
