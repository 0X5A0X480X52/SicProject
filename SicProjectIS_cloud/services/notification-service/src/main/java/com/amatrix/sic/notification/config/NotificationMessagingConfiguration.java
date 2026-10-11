package com.amatrix.sic.notification.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationMessagingConfiguration{
    public static final String EVENT_QUEUE="notification.module-events.q";
    @Bean Queue notificationEventQueue(){return new Queue(EVENT_QUEUE,true,false,false,java.util.Map.of("x-dead-letter-exchange","sic.events.dlx","x-dead-letter-routing-key","notification.module-events.dead"));}
    @Bean Binding notificationEventBinding(Queue notificationEventQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(notificationEventQueue).to(sicEventExchange).with("workflow.module-state-changed");}
    @Bean Binding notificationAuthorizationBinding(Queue notificationEventQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(notificationEventQueue).to(sicEventExchange).with("project.authorization");}
    @Bean Binding notificationIdentityBinding(Queue notificationEventQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(notificationEventQueue).to(sicEventExchange).with("iam.changed");}
    @Bean AnonymousQueue liveNotificationQueue(){return new AnonymousQueue();}
    @Bean Binding liveNotificationBinding(AnonymousQueue liveNotificationQueue,TopicExchange sicEventExchange){return BindingBuilder.bind(liveNotificationQueue).to(sicEventExchange).with("notification.live");}
}
