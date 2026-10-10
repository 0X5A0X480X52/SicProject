package com.amatrix.sic.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.micrometer.core.instrument.MeterRegistry;

import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnBean(JdbcTemplate.class)
public class CloudMessagingAutoConfiguration {
    public static final String DEAD_LETTER_EXCHANGE = "sic.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "sic.events.dead.q";

    @Bean
    @ConditionalOnMissingBean
    TopicExchange sicEventExchange(OutboxProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sicDeadLetterExchange")
    TopicExchange sicDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sicDeadLetterQueue")
    Queue sicDeadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sicDeadLetterBinding")
    Binding sicDeadLetterBinding(Queue sicDeadLetterQueue, TopicExchange sicDeadLetterExchange) {
        return BindingBuilder.bind(sicDeadLetterQueue).to(sicDeadLetterExchange).with("#");
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate) {
        return new OutboxRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxService outboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        return new OutboxService(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumedEventGuard consumedEventGuard(JdbcTemplate jdbcTemplate) {
        return new ConsumedEventGuard(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(
            OutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            OutboxProperties properties) {
        return new OutboxPublisher(repository, rabbitTemplate, properties);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean
    MessagingMetrics messagingMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry,
            ObjectProvider<org.springframework.amqp.rabbit.core.RabbitAdmin> rabbitAdmin,
            Environment environment) {
        return new MessagingMetrics(jdbcTemplate, rabbitAdmin.getIfAvailable(), meterRegistry,
                environment.getProperty("spring.application.name", "unknown-service"));
    }
}
