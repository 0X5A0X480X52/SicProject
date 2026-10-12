package com.amatrix.sic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sic.security 配置：issuer、audience、jwkSetUri；对 issuer/audience 提供默认值。
 */
@ConfigurationProperties("sic.security")
public record CloudSecurityProperties(String issuer, String audience, String jwkSetUri) {
    public CloudSecurityProperties {
        issuer = issuer == null || issuer.isBlank() ? "sic-iam" : issuer;
        audience = audience == null || audience.isBlank() ? "sic-api" : audience;
    }
}
