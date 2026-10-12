package com.amatrix.sic.messaging;

import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;

import tools.jackson.databind.ObjectMapper;

/**
 * 序列化 EventEnvelope 并在事务中写 Outbox，连接本地业务事务与后续消息发布。
 * 在调用方业务事务中写入待发送事件；入库成功不代表 RabbitMQ 已收到消息。
 */
public class OutboxService {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String routingKey, EventEnvelope<?> event) {
        try {
            repository.insert(event.eventId(), event.eventType(), routingKey, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize outbox event " + event.eventType(), ex);
        }
    }
}
