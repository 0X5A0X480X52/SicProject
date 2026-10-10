package com.amatrix.sic.messaging;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.amatrix.sic.messaging.dao.MessagingMapper;

public class OutboxRepository {
    private final MessagingMapper mapper;

    public OutboxRepository(MessagingMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(UUID eventId, String eventType, String routingKey, String payloadJson) {
        mapper.insertOutbox(eventId.toString(),eventType,routingKey,payloadJson,Instant.now());
    }

    /**
     * Claims rows with a compare-and-set update. Multiple service instances may read the same candidate,
     * but only one can acquire its lease. Duplicate delivery can still occur after a lease timeout and is
     * intentionally handled by consumed_event on the consumer side.
     */
    public List<OutboxRecord> claimPending(int limit, String owner, Duration lease) {
        Instant now = Instant.now();
        List<OutboxRecord> candidates = mapper.candidates(now,limit).stream().map(entry ->
                new OutboxRecord(UUID.fromString(entry.eventId()),entry.eventType(),entry.routingKey(),entry.payloadJson(),entry.createdAt(),entry.attempts())).toList();
        List<OutboxRecord> claimed = new ArrayList<>(candidates.size());
        Instant lockedUntil = now.plus(lease);
        for (OutboxRecord candidate : candidates) {
            int updated = mapper.claim(candidate.eventId().toString(),owner,now,lockedUntil);
            if (updated == 1) claimed.add(candidate);
        }
        return List.copyOf(claimed);
    }

    public void markPublished(UUID eventId, String owner) {
        mapper.published(eventId.toString(),owner,Instant.now());
    }

    public void markFailed(UUID eventId, String owner, String error, Instant nextAttemptAt) {
        String safeError = error == null ? "Unknown publication failure" : error;
        if (safeError.length() > 1000) safeError = safeError.substring(0, 1000);
        mapper.failed(eventId.toString(),owner,safeError,nextAttemptAt);
    }
}
