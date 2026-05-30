package com.keystone.auth.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. Validation is intentionally minimal — credential semantics live in the use case.
 */
@Schema(name = "LoginRequest", description = "Credentials submitted to /auth/login")
public record LoginRequest(
    @Schema(example = "alice@example.com", maxLength = 254) @NotBlank @Size(max = 254) String email,
    @Schema(example = "correct-horse-battery-staple", format = "password", maxLength = 256)
        @NotBlank
        @Size(max = 256)
        String password) {}
