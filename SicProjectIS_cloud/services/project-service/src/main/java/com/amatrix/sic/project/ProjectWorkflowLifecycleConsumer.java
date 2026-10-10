package com.amatrix.sic.project;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectWorkflowLifecycleConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final JdbcTemplate jdbc;

    public ProjectWorkflowLifecycleConsumer(ObjectMapper mapper, ConsumedEventGuard guard, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = ProjectSnapshotMessagingConfiguration.WORKFLOW_LIFECYCLE_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {
        });
        if (!"PROJECT_LIFECYCLE_CHANGED".equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected event type on project lifecycle queue");
        }
        if (!guard.begin(event.eventId(), "project-workflow-lifecycle")) return;
        Long projectId = asLong(event.payload().get("projectId"));
        String lifecycleStage = String.valueOf(event.payload().get("lifecycleStage"));
        if (projectId == null || lifecycleStage.isBlank() || "null".equals(lifecycleStage)) {
            throw new IllegalArgumentException("Project lifecycle event is incomplete");
        }
        jdbc.update("UPDATE project SET business_status=?,version=version+1 WHERE id=? AND lifecycle_status='ACTIVE'",
                lifecycleStage, projectId);
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
