package com.amatrix.sic.notification;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PendingNotificationDeliveryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SseConnectionRegistry connections;

    public PendingNotificationDeliveryService(JdbcTemplate jdbc, ObjectMapper mapper,
            SseConnectionRegistry connections) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.connections = connections;
    }

    public void deliver(String eventId, String eventName, Map<String, Object> payload) {
        Long userId = number(payload.get("userId"));
        int delivered = userId == null ? connections.broadcast(eventName, payload)
                : connections.sendTo(userId, eventName, payload);
        if (delivered > 0) markDelivered(eventId, userId == null ? "BROADCASTED" : "DELIVERED");
    }

    public void replayTo(Long userId) {
        for (Pending pending : pendingForUser(userId)) {
            if (connections.sendTo(userId, pending.eventName(), pending.payload()) > 0) {
                markDelivered(pending.eventId(), "DELIVERED");
            }
        }
    }

    @Scheduled(fixedDelayString = "${sic.notification.delivery-delay-ms:5000}")
    public void retryQueued() {
        List<Pending> rows = jdbc.query("""
                SELECT event_id,user_id,event_name,payload_json FROM pending_notification
                 WHERE status='QUEUED' ORDER BY created_at LIMIT 100
                """, (rs, row) -> new Pending(rs.getString("event_id"), nullableLong(rs, "user_id"),
                        rs.getString("event_name"), read(rs.getString("payload_json"))));
        for (Pending pending : rows) {
            int delivered = pending.userId() == null
                    ? connections.broadcast(pending.eventName(), pending.payload())
                    : connections.sendTo(pending.userId(), pending.eventName(), pending.payload());
            if (delivered > 0) {
                markDelivered(pending.eventId(), pending.userId() == null ? "BROADCASTED" : "DELIVERED");
            } else {
                jdbc.update("UPDATE pending_notification SET retry_count=retry_count+1 WHERE event_id=? AND status='QUEUED'",
                        pending.eventId());
            }
        }
    }

    private List<Pending> pendingForUser(Long userId) {
        return jdbc.query("""
                SELECT event_id,user_id,event_name,payload_json FROM pending_notification
                 WHERE status='QUEUED' AND user_id=? ORDER BY created_at LIMIT 100
                """, (rs, row) -> new Pending(rs.getString("event_id"), nullableLong(rs, "user_id"),
                        rs.getString("event_name"), read(rs.getString("payload_json"))), userId);
    }

    private void markDelivered(String eventId, String status) {
        jdbc.update("""
                UPDATE pending_notification SET status=?,delivered_at=CURRENT_TIMESTAMP(6)
                 WHERE event_id=? AND status='QUEUED'
                """, status, eventId);
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read pending notification", ex);
        }
    }

    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private static Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long value=rs.getLong(column);return rs.wasNull()?null:value;}
    private record Pending(String eventId, Long userId, String eventName, Map<String, Object> payload) {}
}
