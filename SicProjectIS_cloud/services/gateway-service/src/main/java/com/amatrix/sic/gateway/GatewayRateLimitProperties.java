package com.amatrix.sic.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sic.gateway.rate-limit 配置：开关、窗口秒数与每窗口请求上限。
 */
@ConfigurationProperties("sic.gateway.rate-limit")
public record GatewayRateLimitProperties(boolean enabled, int requestsPerSecond) {
    public GatewayRateLimitProperties {
        requestsPerSecond = requestsPerSecond <= 0 ? 40 : requestsPerSecond;
    }
}
