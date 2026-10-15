package com.amatrix.sic.notification.messaging.consumer;

import com.amatrix.sic.notification.config.NotificationMessagingConfiguration;
import com.amatrix.sic.notification.service.PendingNotificationDeliveryService;

import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.amatrix.sic.notification.dao.NotificationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;
import com.amatrix.sic.messaging.OutboxService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * store 将模块状态事件（以及由 project.authorization、iam.changed 改写的工作台失效提示）去重持久化并发 notification.live；live 以实例专属 consumerName 去重并触发 SSE 投递。
 * 通知提示只携带刷新所需标识；收到推送后前端仍通过业务接口重新读取和鉴权。
 */
@Component
public class NotificationEventConsumers{
    private final ObjectMapper mapper;private final NotificationMapper notifications;private final ConsumedEventGuard guard;
    private final OutboxService outbox;private final PendingNotificationDeliveryService delivery;
    private final String liveConsumerName="notification-live-delivery:"+UUID.randomUUID();
    public NotificationEventConsumers(ObjectMapper mapper,NotificationMapper notifications,ConsumedEventGuard guard,OutboxService outbox,PendingNotificationDeliveryService delivery){this.mapper=mapper;this.notifications=notifications;this.guard=guard;this.outbox=outbox;this.delivery=delivery;}

    @RabbitListener(queues=NotificationMessagingConfiguration.EVENT_QUEUE)
    @Transactional
    public void store(String json)throws Exception{
        EventEnvelope<Map<String,Object>> source=mapper.readValue(json,new TypeReference<>(){});
        if(!guard.begin(source.eventId(),"notification-module-state"))return;
        Map<String,Object> hint=source.payload();
        if(!"MODULE_STATE_CHANGED".equals(source.eventType())) {
            hint=new java.util.LinkedHashMap<>();hint.put("type","WORKBENCH_INVALIDATED");
            for(String key:java.util.List.of("projectId","userId"))
                if(source.payload().get(key)!=null)hint.put(key,source.payload().get(key));
        }
        String payload=mapper.writeValueAsString(hint);
        Long userId=number(hint.get("userId"));
        notifications.insert(source.eventId().toString(),userId,payload);
        outbox.enqueue("notification.live",EventEnvelope.create("NOTIFICATION_LIVE","NOTIFICATION",source.eventId().toString(),source.correlationId(),source.eventId().toString(),source.actorUserId(),hint));
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
