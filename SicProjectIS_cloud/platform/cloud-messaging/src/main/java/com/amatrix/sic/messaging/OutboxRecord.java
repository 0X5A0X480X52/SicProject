package com.amatrix.sic.messaging;

import java.time.Instant;
import java.util.UUID;

public record OutboxRecord(
        UUID eventId,
        String eventType,
        String routingKey,
        String payloadJson,
        Instant createdAt,
        int attempts) {
}
