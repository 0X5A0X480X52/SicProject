package com.amatrix.sic.dlq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DeadLetterReplayPropertiesTest {
    @Test
    void requiresAnExplicitOperatorConfirmation() {
        DeadLetterReplayProperties properties = new DeadLetterReplayProperties("", 10, Duration.ofSeconds(10));

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DLQ_REPLAY_CONFIRM");
    }

    @Test
    void rejectsAnUnboundedBatch() {
        DeadLetterReplayProperties properties = new DeadLetterReplayProperties(
                DeadLetterReplayProperties.REQUIRED_CONFIRMATION, 1_001, Duration.ofSeconds(10));

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 1000");
    }

    @Test
    void acceptsAConfirmedBoundedBatch() {
        DeadLetterReplayProperties properties = new DeadLetterReplayProperties(
                DeadLetterReplayProperties.REQUIRED_CONFIRMATION, 10, Duration.ofSeconds(10));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
