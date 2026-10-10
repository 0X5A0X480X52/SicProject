package com.amatrix.sic.messaging;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

public class OutboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID eventId, String eventType, String routingKey, String payloadJson) {
        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, event_type, routing_key, payload_json, created_at, attempts)
                VALUES (?, ?, ?, ?, ?, 0)
                """, eventId.toString(), eventType, routingKey, payloadJson, Timestamp.from(Instant.now()));
    }

    /**
     * Claims rows with a compare-and-set update. Multiple service instances may read the same candidate,
     * but only one can acquire its lease. Duplicate delivery can still occur after a lease timeout and is
     * intentionally handled by consumed_event on the consumer side.
     */
    public List<OutboxRecord> claimPending(int limit, String owner, Duration lease) {
        Instant now = Instant.now();
        List<OutboxRecord> candidates = jdbcTemplate.query("""
                SELECT event_id, event_type, routing_key, payload_json, created_at, attempts
                  FROM outbox_event
                 WHERE published_at IS NULL
                   AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                   AND (locked_until IS NULL OR locked_until<?)
                 ORDER BY created_at,event_id
                 LIMIT ?
                """, (rs, row) -> new OutboxRecord(UUID.fromString(rs.getString("event_id")),
                        rs.getString("event_type"), rs.getString("routing_key"), rs.getString("payload_json"),
                        rs.getTimestamp("created_at").toInstant(), rs.getInt("attempts")),
                Timestamp.from(now), Timestamp.from(now), limit);
        List<OutboxRecord> claimed = new ArrayList<>(candidates.size());
        Instant lockedUntil = now.plus(lease);
        for (OutboxRecord candidate : candidates) {
            int updated = jdbcTemplate.update("""
                    UPDATE outbox_event SET lock_owner=?,locked_until=?
                     WHERE event_id=? AND published_at IS NULL
                       AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                       AND (locked_until IS NULL OR locked_until<?)
                    """, owner, Timestamp.from(lockedUntil), candidate.eventId().toString(), Timestamp.from(now),
                    Timestamp.from(now));
            if (updated == 1) claimed.add(candidate);
        }
        return List.copyOf(claimed);
    }

    public void markPublished(UUID eventId, String owner) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET published_at=?,last_error=NULL,lock_owner=NULL,locked_until=NULL
                 WHERE event_id=? AND lock_owner=?
                """, Timestamp.from(Instant.now()), eventId.toString(), owner);
    }

    public void markFailed(UUID eventId, String owner, String error, Instant nextAttemptAt) {
        String safeError = error == null ? "Unknown publication failure" : error;
        if (safeError.length() > 1000) safeError = safeError.substring(0, 1000);
        jdbcTemplate.update("""
                UPDATE outbox_event SET attempts=attempts+1,last_error=?,next_attempt_at=?,lock_owner=NULL,
                  locked_until=NULL WHERE event_id=? AND lock_owner=?
                """, safeError, Timestamp.from(nextAttemptAt), eventId.toString(), owner);
    }
}
