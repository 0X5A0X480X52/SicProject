package com.amatrix.sic.dlq;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.dlq-replay")
record DeadLetterReplayProperties(String confirmation, int maxMessages, Duration confirmTimeout) {
    static final String REQUIRED_CONFIRMATION = "REPLAY_DEAD_LETTERS";

    void validate() {
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalStateException("Set DLQ_REPLAY_CONFIRM=REPLAY_DEAD_LETTERS to authorize replay");
        }
        if (maxMessages < 1 || maxMessages > 1_000) {
            throw new IllegalArgumentException("DLQ_REPLAY_MAX_MESSAGES must be between 1 and 1000");
        }
        if (confirmTimeout == null || confirmTimeout.isNegative() || confirmTimeout.isZero()) {
            throw new IllegalArgumentException("DLQ_REPLAY_CONFIRM_TIMEOUT must be positive");
        }
    }
}
