package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.infrastructure.web.RateLimitFilter;
import com.keystone.auth.infrastructure.web.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Web-tier filter registration. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebConfiguration {

  @Bean
  public RateLimitFilter rateLimitFilter(RateLimitProperties properties) {
    return new RateLimitFilter(properties);
  }

  /**
   * Register the filter explicitly so it runs <em>before</em> Spring Security — a flood of
   * malformed requests should be rejected without paying the authentication cost.
   */
  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/auth/login", "/auth/register");
    return registration;
  }
}
