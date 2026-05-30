package com.keystone.auth.infrastructure.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code keystone.rate-limit.*}. One {@link Rule} per protected path; the {@link
 * RateLimitFilter} matches the request URI against the keys, keyed by client IP.
 *
 * @param login policy applied to {@code POST /auth/login}
 * @param register policy applied to {@code POST /auth/register}
 */
@ConfigurationProperties("keystone.rate-limit")
public record RateLimitProperties(Rule login, Rule register) {

  public record Rule(int capacity, int refillTokens, Duration refillPeriod) {

    public Rule {
      if (capacity <= 0) {
        throw new IllegalArgumentException("capacity must be positive");
      }
      if (refillTokens <= 0) {
        throw new IllegalArgumentException("refillTokens must be positive");
      }
      if (refillPeriod == null || refillPeriod.isNegative() || refillPeriod.isZero()) {
        throw new IllegalArgumentException("refillPeriod must be positive");
      }
    }
  }
}
