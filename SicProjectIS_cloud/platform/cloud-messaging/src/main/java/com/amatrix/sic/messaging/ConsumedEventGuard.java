package com.amatrix.sic.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public class ConsumedEventGuard {
    private final JdbcTemplate jdbcTemplate;

    public ConsumedEventGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean begin(UUID eventId, String consumerName) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO consumed_event (event_id, consumer_name, consumed_at)
                    VALUES (?, ?, ?)
                    """, eventId.toString(), consumerName, Timestamp.from(Instant.now()));
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
