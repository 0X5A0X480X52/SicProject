package com.amatrix.sicprojectis_backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record JwtProperties(String jwtSecret, long jwtExpirationMinutes) {
}
