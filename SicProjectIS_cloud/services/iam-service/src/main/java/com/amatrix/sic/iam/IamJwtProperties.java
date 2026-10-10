package com.amatrix.sic.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.jwt")
public record IamJwtProperties(
        String issuer,
        String audience,
        long expirationMinutes,
        String privateKey,
        String publicKey) {
    public IamJwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "sic-iam" : issuer;
        audience = audience == null || audience.isBlank() ? "sic-api" : audience;
        expirationMinutes = expirationMinutes <= 0 ? 120 : expirationMinutes;
    }
}
