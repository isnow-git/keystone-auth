package com.keystone.auth.infrastructure.web;

import com.keystone.auth.infrastructure.security.RsaKeyMaterial;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the RS256 verification key as an RFC 7517 JWK Set so resource servers can verify tokens
 * locally (see ADR-0001). The signing key never leaves {@code keystone-auth}; only the public
 * modulus + exponent are exposed here.
 *
 * <p>The response is a long-lived public cacheable artifact: clients (typically a Spring Security
 * {@code JwtDecoder}) should re-fetch it only on cache expiry or on a key-rotation signal.
 */
@RestController
@Tag(
    name = "JWKS",
    description = "RFC 7517 key set used by resource servers to verify access tokens locally.")
public class JwksController {

  private static final Duration CACHE_TTL = Duration.ofHours(1);

  private final RsaKeyMaterial keys;

  public JwksController(RsaKeyMaterial keys) {
    this.keys = keys;
  }

  @GetMapping(path = "/auth/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get the RS256 verification key set",
      description =
          "Returns the public component of the signing key as an RFC 7517 JWK Set. Long-lived"
              + " and publicly cacheable (Cache-Control: max-age=3600, public).")
  @ApiResponse(responseCode = "200", description = "JWK Set with one or more RSA verification keys")
  public ResponseEntity<Map<String, Object>> jwks() {
    var jwk =
        new RSAKey.Builder(keys.publicKey())
            .keyID(keys.keyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    var set = new JWKSet(jwk);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
        .body(set.toJSONObject());
  }
}
