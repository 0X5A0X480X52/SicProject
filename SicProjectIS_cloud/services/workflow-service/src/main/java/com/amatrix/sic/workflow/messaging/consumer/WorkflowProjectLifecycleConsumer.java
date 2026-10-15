package com.amatrix.sic.workflow.messaging.consumer;

import com.amatrix.sic.workflow.config.WorkflowSnapshotMessagingConfiguration;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.amatrix.sic.workflow.dao.ProjectSnapshotMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 消费生命周期事件，更新本地 project_snapshot.lifecycle_stage。
 */
@Component
public class WorkflowProjectLifecycleConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final ProjectSnapshotMapper snapshots;

    public WorkflowProjectLifecycleConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectSnapshotMapper snapshots) {
        this.mapper = mapper;
        this.guard = guard;
        this.snapshots = snapshots;
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
        snapshots.lifecycle(Long.valueOf(String.valueOf(projectId)),String.valueOf(lifecycleStage));
    }
}
