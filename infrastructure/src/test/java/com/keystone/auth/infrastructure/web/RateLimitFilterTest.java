package com.keystone.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private static final RateLimitProperties PROPERTIES =
      new RateLimitProperties(
          new RateLimitProperties.Rule(2, 2, Duration.ofMinutes(1)), // login
          new RateLimitProperties.Rule(1, 1, Duration.ofHours(1))); // register

  /** Reusable chain that counts how often the request passed through. */
  private static final class CountingChain implements FilterChain {
    final AtomicInteger forwarded = new AtomicInteger();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
      forwarded.incrementAndGet();
    }
  }

  private static MockHttpServletRequest req(String method, String uri, String ip) {
    var r = new MockHttpServletRequest(method, uri);
    r.setRemoteAddr(ip);
    return r;
  }

  @Test
  void allowsRequestsUpToCapacity() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    for (int i = 0; i < 2; i++) {
      var res = new MockHttpServletResponse();
      filter.doFilter(req("POST", "/auth/login", "1.2.3.4"), res, chain);
      assertThat(res.getStatus()).isEqualTo(200);
    }

    assertThat(chain.forwarded.get()).isEqualTo(2);
  }

  @Test
  void rejectsWith429AfterCapacityIsExhausted() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    for (int i = 0; i < 2; i++) {
      filter.doFilter(req("POST", "/auth/login", "1.2.3.4"), new MockHttpServletResponse(), chain);
    }

    var res = new MockHttpServletResponse();
    filter.doFilter(req("POST", "/auth/login", "1.2.3.4"), res, chain);

    assertThat(res.getStatus()).isEqualTo(429);
    assertThat(res.getHeader("Retry-After")).isNotNull();
    assertThat(Long.parseLong(res.getHeader("Retry-After"))).isPositive();
    assertThat(res.getContentType()).contains("application/problem+json");
    assertThat(res.getContentAsString()).contains("Too Many Requests");
    // Chain was not invoked on the rejected request.
    assertThat(chain.forwarded.get()).isEqualTo(2);
  }

  @Test
  void perIpIsolationMeansSecondIpStartsWithFullBucket() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    // Exhaust IP A.
    for (int i = 0; i < 2; i++) {
      filter.doFilter(req("POST", "/auth/login", "10.0.0.1"), new MockHttpServletResponse(), chain);
    }
    var blocked = new MockHttpServletResponse();
    filter.doFilter(req("POST", "/auth/login", "10.0.0.1"), blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(429);

    // IP B still has its own capacity.
    var res = new MockHttpServletResponse();
    filter.doFilter(req("POST", "/auth/login", "10.0.0.2"), res, chain);
    assertThat(res.getStatus()).isEqualTo(200);
  }

  @Test
  void registerHasItsOwnPolicy() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    // Register capacity = 1.
    filter.doFilter(req("POST", "/auth/register", "9.9.9.9"), new MockHttpServletResponse(), chain);

    var blocked = new MockHttpServletResponse();
    filter.doFilter(req("POST", "/auth/register", "9.9.9.9"), blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(429);
  }

  @Test
  void unrelatedPathsAreNotRateLimited() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    for (int i = 0; i < 10; i++) {
      var res = new MockHttpServletResponse();
      filter.doFilter(req("POST", "/auth/refresh", "1.1.1.1"), res, chain);
      assertThat(res.getStatus()).isEqualTo(200);
    }
    assertThat(chain.forwarded.get()).isEqualTo(10);
  }

  @Test
  void nonPostRequestsBypassTheLimit() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    for (int i = 0; i < 10; i++) {
      var res = new MockHttpServletResponse();
      filter.doFilter(req("GET", "/auth/login", "1.1.1.1"), res, chain);
      assertThat(res.getStatus()).isEqualTo(200);
    }
    assertThat(chain.forwarded.get()).isEqualTo(10);
  }

  @Test
  void honoursForwardedHeaderForClientIp() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    for (int i = 0; i < 2; i++) {
      var r = req("POST", "/auth/login", "10.0.0.99");
      r.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.99");
      filter.doFilter(r, new MockHttpServletResponse(), chain);
    }

    // Same forwarded IP exhausts the bucket regardless of the proxy remote address.
    var blocked = new MockHttpServletResponse();
    var r = req("POST", "/auth/login", "10.0.0.99");
    r.addHeader("X-Forwarded-For", "203.0.113.5");
    filter.doFilter(r, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(429);
  }

  @Test
  void rejectionBodyDoesNotEchoClientInput() throws ServletException, IOException {
    var filter = new RateLimitFilter(PROPERTIES);
    var chain = new CountingChain();

    // Exhaust.
    for (int i = 0; i < 2; i++) {
      filter.doFilter(req("POST", "/auth/login", "1.1.1.1"), new MockHttpServletResponse(), chain);
    }
    var injected = req("POST", "/auth/login", "1.1.1.1");
    injected.setContent("<script>alert(1)</script>".getBytes());
    var res = new MockHttpServletResponse();
    filter.doFilter(injected, res, chain);

    assertThat(res.getStatus()).isEqualTo(429);
    assertThat(res.getContentAsString()).doesNotContain("script");
    assertThat(res.getContentAsString()).doesNotContain("alert");
  }

  @Test
  void clientIpFallsBackToRemoteAddrWhenForwardedAbsent() {
    var r = new MockHttpServletRequest("POST", "/auth/login");
    r.setRemoteAddr("198.51.100.42");
    assertThat(RateLimitFilter.clientIp(r)).isEqualTo("198.51.100.42");
  }

  @Test
  void clientIpFallsBackWhenForwardedBlank() {
    var r = new MockHttpServletRequest("POST", "/auth/login");
    r.setRemoteAddr("198.51.100.42");
    r.addHeader("X-Forwarded-For", "  ");
    assertThat(RateLimitFilter.clientIp(r)).isEqualTo("198.51.100.42");
  }

  @Test
  void rejectsNullProperties() {
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> new RateLimitFilter(null));
  }

  @Test
  void invalidRuleParametersAreRejected() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitProperties.Rule(0, 1, Duration.ofMinutes(1)));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitProperties.Rule(1, 0, Duration.ofMinutes(1)));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RateLimitProperties.Rule(1, 1, Duration.ZERO));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RateLimitProperties.Rule(1, 1, null));
  }

  @SuppressWarnings("unused")
  private static int dummyStatusForOk(HttpServletResponse res) {
    // Mock response defaults to 200 if no setStatus called.
    return res.getStatus();
  }
}
