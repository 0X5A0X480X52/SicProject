package com.amatrix.sic.notification.service;

import com.amatrix.sic.notification.sse.SseConnectionRegistry;
import com.amatrix.sic.notification.dao.NotificationTestMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import tools.jackson.databind.ObjectMapper;

class PendingNotificationDeliveryServiceTest {
    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private SseConnectionRegistry connections;
    private PendingNotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:notification-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE pending_notification(
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36) NOT NULL UNIQUE,user_id BIGINT,
                    event_name VARCHAR(100) NOT NULL,payload_json CLOB NOT NULL,status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    delivered_at TIMESTAMP(6))
                """);
        mapper = new ObjectMapper();
        connections = mock(SseConnectionRegistry.class);
        service = new PendingNotificationDeliveryService(NotificationTestMapper.create(jdbc.getDataSource()), mapper, connections);
    }

    @Test
    void liveDeliveryMarksTheDurableRecordOnlyAfterAConnectionAcceptsIt() throws Exception {
        insert("event-1", 7L, Map.of("userId", 7, "state", "APPROVED"));
        when(connections.sendTo(7L, "MODULE_STATE_CHANGED", Map.of("userId", 7, "state", "APPROVED")))
                .thenReturn(0, 1);

        service.deliver("event-1", "MODULE_STATE_CHANGED", Map.of("userId", 7, "state", "APPROVED"));
        assertThat(status("event-1")).isEqualTo("QUEUED");

        service.deliver("event-1", "MODULE_STATE_CHANGED", Map.of("userId", 7, "state", "APPROVED"));
        assertThat(status("event-1")).isEqualTo("DELIVERED");
        assertThat(deliveredAt("event-1")).isNotNull();
    }

    @Test
    void reconnectReplayIsUserScopedAndLeavesOtherUsersQueued() throws Exception {
        Map<String, Object> userSeven = Map.of("userId", 7, "state", "REVIEW");
        insert("event-7", 7L, userSeven);
        insert("event-8", 8L, Map.of("userId", 8, "state", "DRAFT"));
        when(connections.sendTo(7L, "MODULE_STATE_CHANGED", userSeven)).thenReturn(1);

        service.replayTo(7L);

        assertThat(status("event-7")).isEqualTo("DELIVERED");
        assertThat(status("event-8")).isEqualTo("QUEUED");
        verify(connections, never()).sendTo(8L, "MODULE_STATE_CHANGED", Map.of("userId", 8, "state", "DRAFT"));
    }

    @Test
    void scheduledRetryDeliversReachableUsersAndCountsUnavailableAttempts() throws Exception {
        Map<String, Object> reachable = Map.of("userId", 7, "seq", 3);
        Map<String, Object> unavailable = Map.of("userId", 8, "seq", 4);
        insert("reachable", 7L, reachable);
        insert("unavailable", 8L, unavailable);
        when(connections.sendTo(7L, "MODULE_STATE_CHANGED", reachable)).thenReturn(1);
        when(connections.sendTo(8L, "MODULE_STATE_CHANGED", unavailable)).thenReturn(0);

        service.retryQueued();

        assertThat(status("reachable")).isEqualTo("DELIVERED");
        assertThat(status("unavailable")).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM pending_notification WHERE event_id='unavailable'",
                Integer.class)).isEqualTo(1);
    }

    private void insert(String eventId, Long userId, Map<String, Object> payload) throws Exception {
        jdbc.update("""
                INSERT INTO pending_notification(event_id,user_id,event_name,payload_json,status)
                VALUES (?,?,'MODULE_STATE_CHANGED',?,'QUEUED')
                """, eventId, userId, mapper.writeValueAsString(payload));
    }

    private String status(String eventId) {
        return jdbc.queryForObject("SELECT status FROM pending_notification WHERE event_id=?", String.class, eventId);
    }

    private java.sql.Timestamp deliveredAt(String eventId) {
        return jdbc.queryForObject("SELECT delivered_at FROM pending_notification WHERE event_id=?",
                java.sql.Timestamp.class, eventId);
    }
}
