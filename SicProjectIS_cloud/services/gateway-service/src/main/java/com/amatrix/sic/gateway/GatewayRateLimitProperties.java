package com.amatrix.sic.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.gateway.rate-limit")
public record GatewayRateLimitProperties(boolean enabled, int requestsPerSecond) {
    public GatewayRateLimitProperties {
        requestsPerSecond = requestsPerSecond <= 0 ? 40 : requestsPerSecond;
    }
}
