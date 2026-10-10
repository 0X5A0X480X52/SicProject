package com.amatrix.sic.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ConsumedEventGuardTest {
    private ConsumedEventGuard guard;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:consumed-events-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE consumed_event (
                    event_id VARCHAR(36) NOT NULL,
                    consumer_name VARCHAR(120) NOT NULL,
                    consumed_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (event_id, consumer_name)
                )
                """);
        guard = new ConsumedEventGuard(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void rejectsAnEventAlreadyConsumedByTheSameConsumer() {
        UUID eventId = UUID.randomUUID();

        assertTrue(guard.begin(eventId, "notification-module-state"));
        assertFalse(guard.begin(eventId, "notification-module-state"));
        assertTrue(guard.begin(eventId, "another-consumer"));
    }

    @Test
    void rollsBackTheDeduplicationMarkerWhenBusinessHandlingFails() {
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            assertTrue(guard.begin(eventId, "workflow-handler"));
            throw new IllegalStateException("simulated business failure");
        }));

        assertTrue(guard.begin(eventId, "workflow-handler"));
    }
}
