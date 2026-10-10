package com.amatrix.sic.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import com.amatrix.sic.messaging.dao.MessagingMapper;

public class ConsumedEventGuard {
    private final MessagingMapper mapper;

    public ConsumedEventGuard(MessagingMapper mapper) {
        this.mapper = mapper;
    }

    public boolean begin(UUID eventId, String consumerName) {
        try {
            mapper.consume(eventId.toString(),consumerName,Instant.now());
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
