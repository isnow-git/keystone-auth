package com.keystone.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keystone.auth.infrastructure.configuration.SecurityConfiguration;
import com.keystone.auth.infrastructure.security.RsaKeyMaterial;
import com.nimbusds.jose.jwk.JWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JwksController.class)
@Import({SecurityConfiguration.class, JwksControllerTest.TestConfig.class})
class JwksControllerTest {

  @Autowired MockMvc mockMvc;

  @TestConfiguration
  static class TestConfig {
    @Bean
    RsaKeyMaterial rsaKeyMaterial() throws Exception {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair kp = generator.generateKeyPair();
      return new RsaKeyMaterial(
          "test-kid", (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic());
    }
  }

  @Test
  void exposesAJwkSetWithTheRightShape() throws Exception {
    mockMvc
        .perform(get("/auth/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.keys").isArray())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].use").value("sig"))
        .andExpect(jsonPath("$.keys[0].kid").value("test-kid"))
        .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
        .andExpect(jsonPath("$.keys[0].e").isNotEmpty())
        // No private parameters leaked.
        .andExpect(jsonPath("$.keys[0].d").doesNotExist())
        .andExpect(jsonPath("$.keys[0].p").doesNotExist())
        .andExpect(jsonPath("$.keys[0].q").doesNotExist());
  }

  @Test
  void emitsCachingHeadersSoResourceServersCanReuseTheSet() throws Exception {
    mockMvc
        .perform(get("/auth/.well-known/jwks.json"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=3600")))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")));
  }

  @Test
  void responseParsesAsAJwkSet() throws Exception {
    var body =
        mockMvc
            .perform(get("/auth/.well-known/jwks.json"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var set = JWKSet.parse(body);
    assertThat(set.getKeys()).hasSize(1);
    var key = set.getKeys().get(0);
    assertThat(key.getKeyID()).isEqualTo("test-kid");
    assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
    assertThat(key.isPrivate()).isFalse();
  }
}
