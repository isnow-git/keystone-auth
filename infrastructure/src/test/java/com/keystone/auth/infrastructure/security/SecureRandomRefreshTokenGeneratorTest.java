package com.keystone.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SecureRandomRefreshTokenGeneratorTest {

  private final SecureRandomRefreshTokenGenerator generator =
      new SecureRandomRefreshTokenGenerator();

  @Test
  void producesUrlSafeBase64WithoutPadding() {
    var value = generator.generate();
    // 32 bytes Base64URL without padding = 43 chars.
    assertThat(value).hasSize(43);
    assertThat(value).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void successiveCallsYieldDistinctValues() {
    var values =
        IntStream.range(0, 1000)
            .mapToObj(i -> generator.generate())
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    assertThat(values).hasSize(1000); // collision-free across the run
  }
}
