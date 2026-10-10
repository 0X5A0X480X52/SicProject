package com.amatrix.sic.contracts;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String correlationId,
        String causationId,
        Long actorUserId,
        T payload) {

    public static <T> EventEnvelope<T> create(
            String eventType,
            String aggregateType,
            String aggregateId,
            String correlationId,
            String causationId,
            Long actorUserId,
            T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(), aggregateType, aggregateId,
                correlationId, causationId, actorUserId, payload);
    }
}
