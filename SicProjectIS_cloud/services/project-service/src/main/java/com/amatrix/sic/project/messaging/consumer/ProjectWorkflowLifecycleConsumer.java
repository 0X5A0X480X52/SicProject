package com.amatrix.sic.project.messaging.consumer;

import com.amatrix.sic.project.config.ProjectSnapshotMessagingConfiguration;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 去重消费工作流项目生命周期事件，更新 Project 的 business_status 及版本。
 */
@Component
public class ProjectWorkflowLifecycleConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final ProjectAuthorizationMapper projects;

    public ProjectWorkflowLifecycleConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectAuthorizationMapper projects) {
        this.mapper = mapper;
        this.guard = guard;
        this.projects = projects;
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
        projects.lifecycle(projectId,lifecycleStage);
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
