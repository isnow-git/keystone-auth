package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.application.port.RefreshTokenRepository;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.infrastructure.persistence.jooq.JooqRefreshTokenRepository;
import com.keystone.auth.infrastructure.persistence.jooq.JooqUserRepository;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the jOOQ-backed adapters to the application-layer ports. */
@Configuration
public class PersistenceConfiguration {

  @Bean
  public UserRepository userRepository(DSLContext dsl) {
    return new JooqUserRepository(dsl);
  }

  @Bean
  public RefreshTokenRepository refreshTokenRepository(DSLContext dsl) {
    return new JooqRefreshTokenRepository(dsl);
  }
}
