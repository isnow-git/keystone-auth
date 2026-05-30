package com.keystone.auth.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Body returned to the client on a successful login or refresh. The refresh token rides in a
 * cookie, not in this payload.
 */
@Schema(
    name = "AccessTokenResponse",
    description =
        "Body for /auth/login and /auth/refresh success responses. Refresh token is delivered"
            + " via the Set-Cookie header, not in this object.")
public record AccessTokenResponse(
    @Schema(
            description = "RS256 JWT to be sent as `Authorization: Bearer <accessToken>`",
            example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleXN0b25lLWtleS0xIn0...")
        String accessToken,
    @Schema(description = "Access token expiry (ISO 8601)") Instant expiresAt) {}
