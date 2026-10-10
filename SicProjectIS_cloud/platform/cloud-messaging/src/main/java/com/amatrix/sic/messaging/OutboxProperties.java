package com.amatrix.sic.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.messaging")
public record OutboxProperties(
        String exchange,
        int batchSize,
        long fixedDelayMs,
        boolean publisherEnabled,
        long confirmTimeoutMs,
        long claimLeaseSeconds,
        long maxRetrySeconds) {

    public OutboxProperties {
        exchange = exchange == null || exchange.isBlank() ? "sic.events" : exchange;
        batchSize = batchSize <= 0 ? 100 : batchSize;
        fixedDelayMs = fixedDelayMs <= 0 ? 1000 : fixedDelayMs;
        confirmTimeoutMs = confirmTimeoutMs <= 0 ? 10000 : confirmTimeoutMs;
        claimLeaseSeconds = claimLeaseSeconds <= 0 ? 30 : claimLeaseSeconds;
        maxRetrySeconds = maxRetrySeconds <= 0 ? 300 : maxRetrySeconds;
    }
}
