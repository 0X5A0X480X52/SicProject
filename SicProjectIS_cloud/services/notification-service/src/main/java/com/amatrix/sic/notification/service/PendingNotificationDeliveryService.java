package com.amatrix.sic.notification.service;

import com.amatrix.sic.notification.sse.SseConnectionRegistry;

import java.util.List;
import java.util.Map;

import com.amatrix.sic.notification.dao.NotificationMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PendingNotificationDeliveryService {
    private final NotificationMapper notifications;
    private final ObjectMapper mapper;
    private final SseConnectionRegistry connections;

    public PendingNotificationDeliveryService(NotificationMapper notifications, ObjectMapper mapper,
            SseConnectionRegistry connections) {
        this.notifications = notifications;
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
        List<Pending> rows = pending(null,false);
        for (Pending pending : rows) {
            int delivered = pending.userId() == null
                    ? connections.broadcast(pending.eventName(), pending.payload())
                    : connections.sendTo(pending.userId(), pending.eventName(), pending.payload());
            if (delivered > 0) {
                markDelivered(pending.eventId(), pending.userId() == null ? "BROADCASTED" : "DELIVERED");
            } else {
                notifications.retry(pending.eventId());
            }
        }
    }

    private List<Pending> pendingForUser(Long userId) {
        return pending(userId,true);
    }

    private List<Pending> pending(Long userId,boolean scoped) {
        return notifications.queued(userId,scoped).stream().map(row ->
                new Pending(row.eventId(),row.userId(),row.eventName(),read(row.payloadJson()))).toList();
    }

    private void markDelivered(String eventId, String status) {
        notifications.delivered(eventId,status);
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
