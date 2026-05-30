package com.keystone.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the real Spring application against Testcontainers Postgres and a freshly-generated RSA
 * keypair, then runs the full happy + sad path of the auth flow.
 *
 * <p>Skipped locally when no Docker daemon is reachable; exercised on CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("dockerAvailable")
class AuthFlowE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @TempDir static Path keys;

  private static Path privateKeyPath;
  private static Path publicKeyPath;

  @SuppressWarnings("unused")
  static boolean dockerAvailable() {
    try {
      return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair kp = generator.generateKeyPair();
    privateKeyPath = keys.resolve("private.pem");
    publicKeyPath = keys.resolve("public.pem");
    Files.writeString(privateKeyPath, pem("PRIVATE KEY", kp.getPrivate().getEncoded()));
    Files.writeString(publicKeyPath, pem("PUBLIC KEY", kp.getPublic().getEncoded()));

    registry.add("keystone.jwt.private-key-path", () -> privateKeyPath.toString());
    registry.add("keystone.jwt.public-key-path", () -> publicKeyPath.toString());
    registry.add("keystone.jwt.key-id", () -> "e2e-test-kid");
    registry.add("keystone.jwt.issuer", () -> "keystone-auth-e2e");
    // Tighten the rate limit so the test isn't bounded by production caps.
    registry.add("keystone.rate-limit.login.capacity", () -> 100);
    registry.add("keystone.rate-limit.login.refill-tokens", () -> 100);
    registry.add("keystone.rate-limit.register.capacity", () -> 100);
    registry.add("keystone.rate-limit.register.refill-tokens", () -> 100);
  }

  private static String pem(String label, byte[] bytes) {
    var encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes);
    return "-----BEGIN " + label + "-----\n" + encoded + "\n-----END " + label + "-----\n";
  }

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @Test
  void registerLoginVerifyRefreshReuseLogoutFullFlow() throws Exception {
    var base = "http://localhost:" + port;
    var creds = Map.of("email", "alice@example.com", "password", "correct-horse-battery-staple");

    // -------- 1. Register --------
    var register = post(base + "/auth/register", creds, null);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // -------- 2. Login --------
    var login = post(base + "/auth/login", creds, null);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    var accessToken = extractAccessToken(login.getBody());
    var loginCookie = extractRefreshCookie(login);
    assertThat(accessToken).isNotBlank();
    assertThat(loginCookie).isNotBlank();

    // -------- 3. Access token verifies against the published JWKS --------
    var jwks = rest.getForEntity(base + "/auth/.well-known/jwks.json", String.class);
    assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
    var keySet = JWKSet.parse(jwks.getBody());
    assertThat(keySet.getKeys()).hasSize(1);

    var processor = new DefaultJWTProcessor<SecurityContext>();
    var jwk = (RSAKey) keySet.getKeys().get(0);
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource(keySet)));
    var claims = processor.process(SignedJWT.parse(accessToken), null);
    assertThat(claims.getIssuer()).isEqualTo("keystone-auth-e2e");
    assertThat(claims.getSubject()).isNotBlank();
    @SuppressWarnings("unchecked")
    var roles = (List<String>) claims.getClaim("roles");
    assertThat(roles).containsExactly("ROLE_USER");
    // No PII leaked into the JWT.
    assertThat(claims.getClaim("email")).isNull();
    assertThat(claims.getClaim("password")).isNull();
    assertThat(jwk.toRSAPublicKey()).isNotNull();
    // Defensive: verify the signature explicitly too.
    assertThat(SignedJWT.parse(accessToken).verify(new RSASSAVerifier(jwk.toRSAPublicKey())))
        .isTrue();

    // -------- 4. Refresh rotates the cookie --------
    var refresh = post(base + "/auth/refresh", null, loginCookie);
    assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
    var rotatedCookie = extractRefreshCookie(refresh);
    assertThat(rotatedCookie).isNotEqualTo(loginCookie);

    // -------- 5. Reuse detection: replay the original consumed cookie --------
    var reuse = post(base + "/auth/refresh", null, loginCookie);
    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // The rotated cookie is also dead now — the whole family was revoked.
    var afterReuse = post(base + "/auth/refresh", null, rotatedCookie);
    assertThat(afterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // -------- 6. Logout is idempotent --------
    var freshLogin = post(base + "/auth/login", creds, null);
    var freshCookie = extractRefreshCookie(freshLogin);
    var logout = post(base + "/auth/logout", null, freshCookie);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var refreshAfterLogout = post(base + "/auth/refresh", null, freshCookie);
    assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // -------- 7. No-enumeration: unknown vs wrong password --------
    var unknown =
        post(
            base + "/auth/login",
            Map.of("email", "ghost@example.com", "password", "correct-horse-battery-staple"),
            null);
    var wrongPw =
        post(
            base + "/auth/login",
            Map.of("email", "alice@example.com", "password", "wrong-password-attempt-len"),
            null);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPw.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknown.getBody()).isEqualTo(wrongPw.getBody());
  }

  private org.springframework.http.ResponseEntity<String> post(
      String url, Object body, String refreshCookie) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (refreshCookie != null) {
      headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
    }
    var entity = new HttpEntity<>(body, headers);
    return rest.exchange(url, HttpMethod.POST, entity, String.class);
  }

  private static String extractAccessToken(String body) {
    int idx = body.indexOf("\"accessToken\":\"");
    if (idx < 0) {
      return null;
    }
    int start = idx + "\"accessToken\":\"".length();
    int end = body.indexOf('"', start);
    return body.substring(start, end);
  }

  private static String extractRefreshCookie(org.springframework.http.ResponseEntity<?> r) {
    var setCookie = r.getHeaders().get(HttpHeaders.SET_COOKIE);
    if (setCookie == null) {
      return null;
    }
    for (var cookie : setCookie) {
      if (cookie.startsWith("refresh_token=")) {
        int end = cookie.indexOf(';');
        var value = cookie.substring("refresh_token=".length(), end);
        if (!value.isEmpty()) {
          return value;
        }
      }
    }
    return null;
  }

  private static com.nimbusds.jose.jwk.source.JWKSource<SecurityContext> jwkSource(JWKSet set) {
    return (jwkSelector, context) -> jwkSelector.select(set);
  }
}
