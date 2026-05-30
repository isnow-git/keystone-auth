package com.keystone.auth.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Bean Validation only catches the trivially-malformed cases; the real checks
 * (RFC-shaped email, password policy) live in the application layer so they stay testable without
 * Spring.
 */
@Schema(name = "RegisterRequest", description = "Credentials submitted to /auth/register")
public record RegisterRequest(
    @Schema(example = "alice@example.com", maxLength = 254) @NotBlank @Size(max = 254) String email,
    @Schema(
            example = "correct-horse-battery-staple",
            description = "Plaintext password. Server applies Argon2id; minimum 12 characters.",
            minLength = 12,
            maxLength = 256,
            format = "password")
        @NotBlank
        @Size(min = 1, max = 256)
        String password) {}
