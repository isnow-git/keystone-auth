package com.keystone.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. Validation is intentionally minimal — credential semantics live in the use case.
 */
public record LoginRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 256) String password) {}
