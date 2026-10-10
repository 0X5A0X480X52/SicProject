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
@AutoConfigureAfter({JdbcTemplateAutoConfiguration.class,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnBean(org.mybatis.spring.SqlSessionTemplate.class)
public class CloudMessagingAutoConfiguration {
    public static final String DEAD_LETTER_EXCHANGE = "sic.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "sic.events.dead.q";

    @Bean
    @org.springframework.context.annotation.Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
    static JdbcLatencyObserver jdbcLatencyObserver(Environment environment,ObjectProvider<MeterRegistry> registries) {
        return new JdbcLatencyObserver(environment,registries);
    }

    @Bean
    @ConditionalOnMissingBean
    com.amatrix.sic.messaging.dao.MessagingMapper messagingMapper(org.mybatis.spring.SqlSessionTemplate sessions) throws java.io.IOException {
        var configuration=sessions.getConfiguration();
        if(!configuration.hasMapper(com.amatrix.sic.messaging.dao.MessagingMapper.class)) {
            try(var stream=new org.springframework.core.io.ClassPathResource("mapper/messaging/MessagingMapper.xml").getInputStream()) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream,configuration,"mapper/messaging/MessagingMapper.xml",configuration.getSqlFragments()).parse();
            }
        }
        return sessions.getMapper(com.amatrix.sic.messaging.dao.MessagingMapper.class);
    }

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
    OutboxRepository outboxRepository(com.amatrix.sic.messaging.dao.MessagingMapper mapper) {
        return new OutboxRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxService outboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        return new OutboxService(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumedEventGuard consumedEventGuard(com.amatrix.sic.messaging.dao.MessagingMapper mapper) {
        return new ConsumedEventGuard(mapper);
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
    MessagingMetrics messagingMetrics(com.amatrix.sic.messaging.dao.MessagingMapper mapper, MeterRegistry meterRegistry,
            ObjectProvider<org.springframework.amqp.rabbit.core.RabbitAdmin> rabbitAdmin,
            Environment environment) {
        return new MessagingMetrics(mapper, rabbitAdmin.getIfAvailable(), meterRegistry,
                environment.getProperty("spring.application.name", "unknown-service"));
    }
}
