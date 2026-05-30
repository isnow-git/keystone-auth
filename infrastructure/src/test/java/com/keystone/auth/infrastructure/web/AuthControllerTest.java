package com.keystone.auth.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keystone.auth.application.usecase.LoginResult;
import com.keystone.auth.application.usecase.LoginUseCase;
import com.keystone.auth.application.usecase.LogoutUseCase;
import com.keystone.auth.application.usecase.RefreshResult;
import com.keystone.auth.application.usecase.RefreshUseCase;
import com.keystone.auth.application.usecase.RegisterUseCase;
import com.keystone.auth.application.usecase.RegistrationResult;
import com.keystone.auth.domain.model.AccessToken;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.FamilyId;
import com.keystone.auth.domain.model.HashedPassword;
import com.keystone.auth.domain.model.RefreshTokenValue;
import com.keystone.auth.domain.model.TokenHash;
import com.keystone.auth.domain.model.TokenPair;
import com.keystone.auth.domain.model.User;
import com.keystone.auth.infrastructure.configuration.SecurityConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AuthController.class, GlobalExceptionHandler.class})
@Import({SecurityConfiguration.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final HashedPassword PASSWORD =
      new HashedPassword("$argon2id$v=19$m=65536,t=3,p=1$AAA$BBB");

  @TestConfiguration
  static class TestConfig {
    @Bean
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mockMvc;

  @MockitoBean RegisterUseCase registerUseCase;
  @MockitoBean LoginUseCase loginUseCase;
  @MockitoBean RefreshUseCase refreshUseCase;
  @MockitoBean LogoutUseCase logoutUseCase;

  // ---------- /auth/register ----------

  @Test
  void registerSuccessReturns201() throws Exception {
    given(registerUseCase.execute(any(), any()))
        .willReturn(
            new RegistrationResult.Success(
                User.register(Email.of("alice@example.com"), PASSWORD, NOW)));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"alice@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void registerInvalidEmailReturns400() throws Exception {
    given(registerUseCase.execute(any(), any()))
        .willReturn(new RegistrationResult.InvalidEmail("bad"));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"alice@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void registerWeakPasswordReturns422() throws Exception {
    given(registerUseCase.execute(any(), any()))
        .willReturn(new RegistrationResult.WeakPassword("too short"));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void registerEmailAlreadyTakenReturns409WithGenericMessage() throws Exception {
    given(registerUseCase.execute(any(), any()))
        .willReturn(new RegistrationResult.EmailAlreadyTaken());

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Registration could not be completed"));
  }

  @Test
  void registerWithEmptyBodyReturns400() throws Exception {
    mockMvc
        .perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    Mockito.verifyNoInteractions(registerUseCase);
  }

  // ---------- /auth/login ----------

  @Test
  void loginSuccessReturns200WithAccessTokenBodyAndRefreshCookie() throws Exception {
    var tokens = sampleTokenPair();
    given(loginUseCase.execute(any(), any()))
        .willReturn(
            new LoginResult.Success(com.keystone.auth.domain.model.UserId.random(), tokens));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value(tokens.access().value()))
        .andExpect(jsonPath("$.expiresAt").exists())
        .andExpect(cookie().exists("refresh_token"))
        .andExpect(cookie().httpOnly("refresh_token", true))
        .andExpect(cookie().secure("refresh_token", true))
        .andExpect(cookie().path("refresh_token", "/auth/refresh"))
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")));
  }

  @Test
  void loginInvalidCredentialsReturns401() throws Exception {
    given(loginUseCase.execute(any(), any())).willReturn(new LoginResult.InvalidCredentials());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"correct-horse-battery-staple\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(cookie().doesNotExist("refresh_token"));
  }

  // ---------- /auth/refresh ----------

  @Test
  void refreshWithoutCookieReturns401() throws Exception {
    mockMvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    Mockito.verifyNoInteractions(refreshUseCase);
  }

  @Test
  void refreshSuccessRotatesCookieAndReturnsNewAccessToken() throws Exception {
    var tokens = sampleTokenPair();
    given(refreshUseCase.execute(any())).willReturn(new RefreshResult.Success(tokens));

    mockMvc
        .perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "presented")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value(tokens.access().value()))
        .andExpect(cookie().exists("refresh_token"))
        .andExpect(cookie().path("refresh_token", "/auth/refresh"));
  }

  @Test
  void refreshFailureBranchesAllReturnSameOpaque401WithClearedCookie() throws Exception {
    RefreshResult[] failures =
        new RefreshResult[] {
          new RefreshResult.TokenNotFound(),
          new RefreshResult.Expired(),
          new RefreshResult.Revoked(),
          new RefreshResult.ReuseDetected(FamilyId.random()),
        };

    for (var failure : failures) {
      Mockito.reset(refreshUseCase);
      given(refreshUseCase.execute(any())).willReturn(failure);

      mockMvc
          .perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "presented")))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Authentication required"))
          .andExpect(cookie().maxAge("refresh_token", 0));
    }
  }

  // ---------- /auth/logout ----------

  @Test
  void logoutAlwaysReturns204AndClearsCookie() throws Exception {
    mockMvc
        .perform(post("/auth/logout").cookie(new Cookie("refresh_token", "presented")))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresh_token", 0));
  }

  @Test
  void logoutWithoutCookieStillReturns204() throws Exception {
    mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
  }

  // ---------- helpers ----------

  private static TokenPair sampleTokenPair() {
    var access = new AccessToken("jwt-value", NOW.plus(Duration.ofMinutes(15)));
    var refresh =
        new RefreshTokenValue(
            "refresh-plaintext-1", new TokenHash("a".repeat(64)), NOW.plus(Duration.ofDays(7)));
    return new TokenPair(access, refresh);
  }
}
