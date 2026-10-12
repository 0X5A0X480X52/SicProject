package com.amatrix.sic.iam.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sic.jwt 的 issuer、audience、有效分钟数、公私钥配置；有效期默认 120 分钟。
 */
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
