package com.amatrix.sic.notification;

import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;
import com.amatrix.sic.messaging.OutboxService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationEventConsumers{
    private final ObjectMapper mapper;private final JdbcTemplate jdbc;private final ConsumedEventGuard guard;
    private final OutboxService outbox;private final PendingNotificationDeliveryService delivery;
    private final String liveConsumerName="notification-live-delivery:"+UUID.randomUUID();
    public NotificationEventConsumers(ObjectMapper mapper,JdbcTemplate jdbc,ConsumedEventGuard guard,OutboxService outbox,PendingNotificationDeliveryService delivery){this.mapper=mapper;this.jdbc=jdbc;this.guard=guard;this.outbox=outbox;this.delivery=delivery;}

    @RabbitListener(queues=NotificationMessagingConfiguration.EVENT_QUEUE)
    @Transactional
    public void store(String json)throws Exception{
        EventEnvelope<Map<String,Object>> source=mapper.readValue(json,new TypeReference<>(){});
        if(!guard.begin(source.eventId(),"notification-module-state"))return;
        String payload=mapper.writeValueAsString(source.payload());
        Long userId=number(source.payload().get("userId"));
        jdbc.update("INSERT INTO pending_notification(event_id,user_id,event_name,payload_json,status) VALUES (?,?,'MODULE_STATE_CHANGED',?,'QUEUED')",source.eventId().toString(),userId,payload);
        outbox.enqueue("notification.live",EventEnvelope.create("NOTIFICATION_LIVE","NOTIFICATION",source.eventId().toString(),source.correlationId(),source.eventId().toString(),source.actorUserId(),source.payload()));
    }

    @RabbitListener(queues="#{liveNotificationQueue.name}")
    @Transactional
    public void live(String json)throws Exception{
        EventEnvelope<Map<String,Object>> event=mapper.readValue(json,new TypeReference<>(){});
        if(!guard.begin(event.eventId(),liveConsumerName))return;
        delivery.deliver(event.aggregateId(),"MODULE_STATE_CHANGED",event.payload());
    }

    private static Long number(Object value){return value instanceof Number number?number.longValue():null;}
}
