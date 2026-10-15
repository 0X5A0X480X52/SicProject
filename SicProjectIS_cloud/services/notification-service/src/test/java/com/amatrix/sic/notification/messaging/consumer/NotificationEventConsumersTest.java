package com.amatrix.sic.notification.messaging.consumer;

import com.amatrix.sic.notification.service.PendingNotificationDeliveryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;
import com.amatrix.sic.messaging.OutboxService;

import tools.jackson.databind.ObjectMapper;

class NotificationEventConsumersTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void authorizationChangesSendOnlyATargetedRefreshHintWithoutAuditOrIdentityDetails() throws Exception {
        var guard=mock(ConsumedEventGuard.class);
        var notifications=mock(com.amatrix.sic.notification.dao.NotificationMapper.class);
        var outbox=mock(OutboxService.class);
        var event=EventEnvelope.create("PROJECT_GRANT_CHANGED","PROJECT","7",null,null,42L,
                Map.<String,Object>of("projectId",7L,"userId",9L,"before","private-role-history","email","private@example.invalid"));
        when(guard.begin(eq(event.eventId()),anyString())).thenReturn(true);
        var consumer=new NotificationEventConsumers(mapper,notifications,guard,outbox,mock(PendingNotificationDeliveryService.class));
        consumer.store(mapper.writeValueAsString(event));
        var payload=ArgumentCaptor.forClass(String.class);
        verify(notifications).insert(eq(event.eventId().toString()),eq(9L),payload.capture());
        assertThat(payload.getValue()).contains("WORKBENCH_INVALIDATED","projectId","userId")
                .doesNotContain("private","email","before");
        verify(outbox).enqueue(eq("notification.live"),org.mockito.ArgumentMatchers.any());
    }

    @Test
    void liveDeliveryDeduplicatesPerInstanceWithoutSuppressingOtherInstances() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = liveEvent(eventId);
        ConsumedEventGuard guard = mock(ConsumedEventGuard.class);
        PendingNotificationDeliveryService delivery = mock(PendingNotificationDeliveryService.class);
        when(guard.begin(eq(eventId), anyString())).thenReturn(true);
        NotificationEventConsumers first = consumer(guard, delivery);
        NotificationEventConsumers second = consumer(guard, delivery);

        first.live(json);
        second.live(json);

        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(guard, times(2)).begin(eq(eventId), names.capture());
        assertThat(names.getAllValues()).hasSize(2).doesNotHaveDuplicates()
                .allMatch(name -> name.startsWith("notification-live-delivery:"));
        verify(delivery, times(2)).deliver("source-event", "MODULE_STATE_CHANGED",
                Map.of("projectId", 7, "moduleInstanceId", 9));
    }

    @Test
    void duplicateDeliveryOnTheSameInstanceIsIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        ConsumedEventGuard guard = mock(ConsumedEventGuard.class);
        PendingNotificationDeliveryService delivery = mock(PendingNotificationDeliveryService.class);
        when(guard.begin(eq(eventId), anyString())).thenReturn(true, false);
        NotificationEventConsumers consumer = consumer(guard, delivery);

        consumer.live(liveEvent(eventId));
        consumer.live(liveEvent(eventId));

        verify(delivery).deliver("source-event", "MODULE_STATE_CHANGED",
                Map.of("projectId", 7, "moduleInstanceId", 9));
    }

    private NotificationEventConsumers consumer(ConsumedEventGuard guard,
            PendingNotificationDeliveryService delivery) {
        return new NotificationEventConsumers(mapper, mock(com.amatrix.sic.notification.dao.NotificationMapper.class), guard,
                mock(OutboxService.class), delivery);
    }

    private String liveEvent(UUID eventId) throws Exception {
        EventEnvelope<Map<String, Object>> event = new EventEnvelope<>(eventId, "NOTIFICATION_LIVE", 1,
                java.time.Instant.now(), "NOTIFICATION", "source-event", "correlation", "cause", 42L,
                Map.of("projectId", 7, "moduleInstanceId", 9));
        return mapper.writeValueAsString(event);
    }
}
