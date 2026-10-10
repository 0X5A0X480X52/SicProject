package com.amatrix.sic.messaging;

import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;

import tools.jackson.databind.ObjectMapper;

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
