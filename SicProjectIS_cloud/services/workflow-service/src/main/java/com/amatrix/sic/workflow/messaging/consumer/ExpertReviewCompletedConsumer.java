package com.amatrix.sic.workflow.messaging.consumer;

import com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService;
import com.amatrix.sic.workflow.config.WorkflowSnapshotMessagingConfiguration;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExpertReviewCompletedConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final WorkflowRuntimeService runtime;

    public ExpertReviewCompletedConsumer(ObjectMapper mapper, ConsumedEventGuard guard,
            WorkflowRuntimeService runtime) {
        this.mapper = mapper;
        this.guard = guard;
        this.runtime = runtime;
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.EXPERT_REVIEW_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {
        });
        if (!"EXPERT_REVIEW_COMPLETED".equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected event type on expert review queue");
        }
        if (!guard.begin(event.eventId(), "workflow-expert-review-auto-transition")) return;
        Long batchId = asLong(event.payload().get("batchId"));
        if (batchId == null) throw new IllegalArgumentException("batchId is missing");
        runtime.autoTransitionAfterExpertReviewComplete(batchId);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
