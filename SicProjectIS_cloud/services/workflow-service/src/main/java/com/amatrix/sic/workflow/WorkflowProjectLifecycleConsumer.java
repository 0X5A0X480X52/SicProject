package com.amatrix.sic.workflow;

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
public class WorkflowProjectLifecycleConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final JdbcTemplate jdbc;

    public WorkflowProjectLifecycleConsumer(ObjectMapper mapper, ConsumedEventGuard guard, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.PROJECT_LIFECYCLE_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {
        });
        if (!guard.begin(event.eventId(), "workflow-project-lifecycle")) return;
        Object projectId = event.payload().get("projectId");
        Object lifecycleStage = event.payload().get("lifecycleStage");
        if (projectId == null || lifecycleStage == null) return;
        jdbc.update("UPDATE project_snapshot SET lifecycle_stage=?,updated_at=CURRENT_TIMESTAMP(6) WHERE project_id=?",
                String.valueOf(lifecycleStage), Long.valueOf(String.valueOf(projectId)));
    }
}
