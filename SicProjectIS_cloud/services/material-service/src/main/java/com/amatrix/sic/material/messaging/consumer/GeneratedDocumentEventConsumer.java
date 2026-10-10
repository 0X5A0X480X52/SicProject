package com.amatrix.sic.material.messaging.consumer;

import com.amatrix.sic.material.service.document.GeneratedDocumentService;
import com.amatrix.sic.material.config.MaterialMessagingConfiguration;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.DocumentGenerationCommand;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeneratedDocumentEventConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final GeneratedDocumentService documents;

    public GeneratedDocumentEventConsumer(ObjectMapper mapper, ConsumedEventGuard guard,
            GeneratedDocumentService documents) {
        this.mapper = mapper;
        this.guard = guard;
        this.documents = documents;
    }

    @RabbitListener(queues = MaterialMessagingConfiguration.DOCUMENT_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<DocumentGenerationCommand> event = mapper.readValue(json, new TypeReference<>() {
        });
        if (!"DOCUMENT_GENERATION_REQUESTED".equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected event type on document generation queue");
        }
        if (!guard.begin(event.eventId(), "material-document-generation")) return;
        documents.generate(event.payload());
    }
}
