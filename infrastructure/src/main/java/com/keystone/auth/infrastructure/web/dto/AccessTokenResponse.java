package com.keystone.auth.infrastructure.web.dto;

import java.time.Instant;

/**
 * Body returned to the client on a successful login or refresh. The refresh token rides in a
 * cookie, not in this payload.
 */
public record AccessTokenResponse(String accessToken, Instant expiresAt) {}
