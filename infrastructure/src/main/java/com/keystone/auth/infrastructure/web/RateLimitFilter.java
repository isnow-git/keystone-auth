package com.keystone.auth.infrastructure.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP token-bucket rate limit on the credential-exposing endpoints (see {@link
 * RateLimitProperties}).
 *
 * <p>Buckets are held in-memory keyed by {@code path + "|" + clientIp}; the deployment target is a
 * single instance behind a load balancer (the spec is explicit on this). Scaling out would warrant
 * a distributed proxy bucket — out of scope here.
 *
 * <p>On rejection, the filter writes {@code 429 Too Many Requests} with a {@code Retry-After}
 * header (seconds-to-refill) and an {@code application/problem+json} body. The body never echoes
 * client input.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/auth/login";
  private static final String REGISTER_PATH = "/auth/register";
  private static final String PROBLEM_BODY =
      "{\"type\":\"about:blank\","
          + "\"title\":\"Too Many Requests\","
          + "\"status\":429,"
          + "\"detail\":\"Rate limit exceeded for this endpoint. Retry later.\"}";

  private final RateLimitProperties properties;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!HttpMethod.POST.matches(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    var rule = ruleFor(request.getRequestURI());
    if (rule == null) {
      chain.doFilter(request, response);
      return;
    }

    var bucket =
        buckets.computeIfAbsent(
            request.getRequestURI() + "|" + clientIp(request), key -> buildBucket(rule));

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }

    var retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    response.setStatus(429);
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(PROBLEM_BODY);
  }

  private RateLimitProperties.Rule ruleFor(String uri) {
    if (LOGIN_PATH.equals(uri)) {
      return properties.login();
    }
    if (REGISTER_PATH.equals(uri)) {
      return properties.register();
    }
    return null;
  }

  private static Bucket buildBucket(RateLimitProperties.Rule rule) {
    var bandwidth =
        Bandwidth.builder()
            .capacity(rule.capacity())
            .refillGreedy(rule.refillTokens(), rule.refillPeriod())
            .build();
    return Bucket.builder().addLimit(bandwidth).build();
  }

  /**
   * Resolve the client IP. Honours {@code X-Forwarded-For} (first hop) when the deployment sits
   * behind a trusted reverse proxy; falls back to {@code remoteAddr} otherwise.
   */
  static String clientIp(HttpServletRequest request) {
    var forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      var first = forwarded.split(",")[0].trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    var remote = request.getRemoteAddr();
    return remote != null ? remote : "unknown";
  }
}
