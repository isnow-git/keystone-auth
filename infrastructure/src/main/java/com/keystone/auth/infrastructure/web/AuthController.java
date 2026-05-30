package com.keystone.auth.infrastructure.web;

import com.keystone.auth.application.usecase.LoginResult;
import com.keystone.auth.application.usecase.LoginUseCase;
import com.keystone.auth.application.usecase.LogoutUseCase;
import com.keystone.auth.application.usecase.RefreshResult;
import com.keystone.auth.application.usecase.RefreshUseCase;
import com.keystone.auth.application.usecase.RegisterUseCase;
import com.keystone.auth.application.usecase.RegistrationResult;
import com.keystone.auth.infrastructure.web.dto.AccessTokenResponse;
import com.keystone.auth.infrastructure.web.dto.LoginRequest;
import com.keystone.auth.infrastructure.web.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the auth flow. Every endpoint maps a sealed use-case result to a
 * deliberately-narrow HTTP shape so the REST adapter does not leak internal state.
 *
 * <p>Cookie discipline lives in {@link RefreshCookies}: the refresh token rides in an {@code
 * HttpOnly; Secure; SameSite=Strict; Path=/auth/refresh} cookie and never in the response body.
 */
@RestController
@RequestMapping("/auth")
@Tag(
    name = "Authentication",
    description = "Register, login, refresh, logout — the full auth flow.")
public class AuthController {

  private final RegisterUseCase registerUseCase;
  private final LoginUseCase loginUseCase;
  private final RefreshUseCase refreshUseCase;
  private final LogoutUseCase logoutUseCase;
  private final Clock clock;

  public AuthController(
      RegisterUseCase registerUseCase,
      LoginUseCase loginUseCase,
      RefreshUseCase refreshUseCase,
      LogoutUseCase logoutUseCase,
      Clock clock) {
    this.registerUseCase = registerUseCase;
    this.loginUseCase = loginUseCase;
    this.refreshUseCase = refreshUseCase;
    this.logoutUseCase = logoutUseCase;
    this.clock = clock;
  }

  @PostMapping("/register")
  @Operation(
      summary = "Register a new user",
      description =
          "Validates the email + password (policy: 12–128 chars, no control chars), hashes the"
              + " password with Argon2id, persists the user with ROLE_USER.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "User created"),
    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid email"),
    @ApiResponse(responseCode = "409", description = "Email already registered (generic body)"),
    @ApiResponse(responseCode = "422", description = "Password does not meet the policy"),
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded (3 / hour / IP)")
  })
  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
    return switch (registerUseCase.execute(request.email(), request.password())) {
      case RegistrationResult.Success ignored -> ResponseEntity.status(HttpStatus.CREATED).build();
      case RegistrationResult.InvalidEmail ignored ->
          problem(HttpStatus.BAD_REQUEST, "Invalid email");
      case RegistrationResult.WeakPassword ignored ->
          problem(HttpStatus.UNPROCESSABLE_ENTITY, "Password does not meet the policy");
      case RegistrationResult.EmailAlreadyTaken ignored ->
          problem(HttpStatus.CONFLICT, "Registration could not be completed");
    };
  }

  @PostMapping("/login")
  @Operation(
      summary = "Exchange credentials for an access + refresh pair",
      description =
          "Returns a JSON body with the RS256 access token (15-minute TTL) and a refresh cookie"
              + " (HttpOnly, Secure, SameSite=Strict, Path=/auth/refresh, 7-day TTL). Unknown"
              + " email and wrong password collapse to the same 401 response (no enumeration).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Access token issued; refresh cookie set"),
    @ApiResponse(responseCode = "400", description = "Malformed JSON"),
    @ApiResponse(responseCode = "401", description = "Invalid credentials"),
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded (5 / minute / IP)")
  })
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    return switch (loginUseCase.execute(request.email(), request.password())) {
      case LoginResult.Success success -> tokenResponse(success.tokens());
      case LoginResult.InvalidCredentials ignored ->
          problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    };
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Rotate the refresh token, get a new access token",
      description =
          "Reads the refresh cookie, looks the token up by SHA-256 hash, calls the rotation"
              + " state machine (ADR-0005). On success returns a new access token and a rotated"
              + " refresh cookie. Every failure mode — not found, expired, revoked, *reuse"
              + " detected* — collapses to the same opaque 401 so the wire cannot distinguish"
              + " benign expiry from suspected theft.")
  @SecurityRequirement(name = "refreshCookie")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Token rotated; new pair returned"),
    @ApiResponse(
        responseCode = "401",
        description = "Refresh failed (opaque — same body for every failure variant)")
  })
  public ResponseEntity<?> refresh(HttpServletRequest request) {
    var plaintext = readRefreshCookie(request);
    if (plaintext == null) {
      return problem(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    // Every failure variant collapses to the same opaque response: a client must not be able to
    // distinguish "expired" from "reuse detected" on the wire (see ADR-0005).
    return switch (refreshUseCase.execute(plaintext)) {
      case RefreshResult.Success success -> tokenResponse(success.tokens());
      case RefreshResult.TokenNotFound ignored ->
          problemWithClearedCookie(HttpStatus.UNAUTHORIZED, "Authentication required");
      case RefreshResult.Expired ignored ->
          problemWithClearedCookie(HttpStatus.UNAUTHORIZED, "Authentication required");
      case RefreshResult.Revoked ignored ->
          problemWithClearedCookie(HttpStatus.UNAUTHORIZED, "Authentication required");
      case RefreshResult.ReuseDetected ignored ->
          problemWithClearedCookie(HttpStatus.UNAUTHORIZED, "Authentication required");
    };
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Revoke the active refresh-token family",
      description =
          "Idempotent: always returns 204 regardless of whether the cookie was present or"
              + " already revoked. Prevents probing account existence through the logout"
              + " endpoint.")
  @SecurityRequirement(name = "refreshCookie")
  @ApiResponse(responseCode = "204", description = "Cookie cleared, family revoked if found")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    logoutUseCase.execute(readRefreshCookie(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, RefreshCookies.clearingCookie().toString())
        .build();
  }

  private ResponseEntity<?> tokenResponse(com.keystone.auth.domain.model.TokenPair tokens) {
    ResponseCookie cookie = RefreshCookies.freshCookie(tokens.refresh(), clock.instant());
    var body = new AccessTokenResponse(tokens.access().value(), tokens.access().expiresAt());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title) {
    var problem = ProblemDetail.forStatusAndDetail(status, title);
    return ResponseEntity.status(status).body(problem);
  }

  private static ResponseEntity<ProblemDetail> problemWithClearedCookie(
      HttpStatus status, String title) {
    var problem = ProblemDetail.forStatusAndDetail(status, title);
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, RefreshCookies.clearingCookie().toString())
        .body(problem);
  }

  private static String readRefreshCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (RefreshCookies.NAME.equals(cookie.getName())) {
        var value = cookie.getValue();
        return (value == null || value.isBlank()) ? null : value;
      }
    }
    return null;
  }
}
