package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.infrastructure.security.Argon2PasswordHasher;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Crypto + clock beans used across the application layer. */
@Configuration
public class SecurityCryptoConfiguration {

  @Bean
  public PasswordHasher passwordHasher() {
    return new Argon2PasswordHasher();
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
