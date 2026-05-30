package com.keystone.auth.infrastructure.configuration;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HTTP security: no sessions, no CSRF (only token-bearing endpoints), explicit allow-list
 * for unauthenticated routes.
 *
 * <p>The {@code /auth/.well-known/jwks.json} endpoint is intentionally public — its whole purpose
 * is to be fetched by anonymous resource servers (see ADR-0001). Auth-flow endpoints ({@code
 * /auth/login}, {@code /auth/register}, {@code /auth/refresh}, {@code /auth/logout}) are also
 * public; they perform their own credential / cookie checks.
 *
 * <p>Everything else requires authentication, deferring the verification logic to the OAuth2
 * Resource Server starter (configured via the JWKS URI once consumers join the cluster).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        antMatcher("/auth/.well-known/**"),
                        antMatcher("/auth/register"),
                        antMatcher("/auth/login"),
                        antMatcher("/auth/refresh"),
                        antMatcher("/auth/logout"),
                        antMatcher("/actuator/health/**"),
                        antMatcher("/actuator/info"),
                        // springdoc OpenAPI + Swagger UI
                        antMatcher("/v3/api-docs/**"),
                        antMatcher("/swagger-ui.html"),
                        antMatcher("/swagger-ui/**"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
