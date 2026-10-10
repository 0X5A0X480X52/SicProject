package com.amatrix.sic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.security")
public record CloudSecurityProperties(String issuer, String audience, String jwkSetUri) {
    public CloudSecurityProperties {
        issuer = issuer == null || issuer.isBlank() ? "sic-iam" : issuer;
        audience = audience == null || audience.isBlank() ? "sic-api" : audience;
    }
}
