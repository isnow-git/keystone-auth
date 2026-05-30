package com.keystone.auth.infrastructure.configuration;

import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.application.usecase.RegisterUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the use cases. Use-case classes themselves stay free of Spring annotations. */
@Configuration
public class ApplicationConfiguration {

  @Bean
  public RegisterUseCase registerUseCase(
      UserRepository userRepository, PasswordHasher passwordHasher, Clock clock) {
    return new RegisterUseCase(userRepository, passwordHasher, clock);
  }
}
