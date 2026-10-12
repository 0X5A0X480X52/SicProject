package com.amatrix.sic.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sic.security 的 issuer/audience 与公开路径配置。
 */
@ConfigurationProperties("sic.security")
public record GatewaySecurityProperties(String issuer, String audience, String jwkSetUri) {
    public GatewaySecurityProperties {
        issuer = issuer == null || issuer.isBlank() ? "sic-iam" : issuer;
        audience = audience == null || audience.isBlank() ? "sic-api" : audience;
    }
}
