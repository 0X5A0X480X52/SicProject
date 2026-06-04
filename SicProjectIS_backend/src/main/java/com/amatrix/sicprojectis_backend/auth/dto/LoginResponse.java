package com.amatrix.sicprojectis_backend.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, CurrentUserResponse user) {
}
