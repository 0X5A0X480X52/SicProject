package com.amatrix.sic.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.security")
public record GatewaySecurityProperties(String issuer, String audience, String jwkSetUri) {
    public GatewaySecurityProperties {
        issuer = issuer == null || issuer.isBlank() ? "sic-iam" : issuer;
        audience = audience == null || audience.isBlank() ? "sic-api" : audience;
    }
}
