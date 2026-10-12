package com.amatrix.sic.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时领取待发事件，使用 RabbitMQ publisher confirm 后标记成功；失败指数退避，中断恢复线程标识。
 * 数据库发送租约与 RabbitMQ 确认之间存在失败窗口，事件可能重复投递，消费方必须去重。
 */
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final String publisherId = UUID.randomUUID().toString();

    public OutboxPublisher(OutboxRepository repository, RabbitTemplate rabbitTemplate, OutboxProperties properties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /**
     * 领取本服务 Outbox 发送租约后逐项投递，根据 RabbitMQ 确认记录成功或重试。发送成功与数据库标记之间仍可能发生重复投递。
     */
    @Scheduled(fixedDelayString = "${sic.messaging.fixed-delay-ms:1000}")
    public void publishPending() {
        if (!properties.publisherEnabled()) return;
        var lease = Duration.ofSeconds(properties.claimLeaseSeconds());
        for (OutboxRecord record : repository.claimPending(properties.batchSize(), publisherId, lease)) {
            try {
                publishConfirmed(record);
                repository.markPublished(record.eventId(), publisherId);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failed(record, "Publisher interrupted while waiting for broker confirmation");
                return;
            } catch (Exception ex) {
                failed(record, ex.getMessage());
                log.warn("Outbox publication failed for {}", record.eventId(), ex);
            }
        }
    }

    private void publishConfirmed(OutboxRecord record) throws Exception {
        CorrelationData correlation = new CorrelationData(record.eventId().toString());
        rabbitTemplate.convertAndSend(properties.exchange(), record.routingKey(), record.payloadJson(), message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setMessageId(record.eventId().toString());
            message.getMessageProperties().setType(record.eventType());
            return message;
        }, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(properties.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!confirm.ack()) {
            throw new AmqpException("RabbitMQ rejected event: " + confirm.reason());
        }
    }

    private void failed(OutboxRecord record, String error) {
        long exponent = Math.min(20, Math.max(0, record.attempts()));
        long delay = Math.min(properties.maxRetrySeconds(), 1L << exponent);
        repository.markFailed(record.eventId(), publisherId, error, Instant.now().plusSeconds(delay));
    }
}
