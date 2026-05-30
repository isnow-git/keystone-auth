package com.keystone.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Bean Validation only catches the trivially-malformed cases; the real checks
 * (RFC-shaped email, password policy) live in the application layer so they stay testable without
 * Spring.
 */
public record RegisterRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 1, max = 256) String password) {}
