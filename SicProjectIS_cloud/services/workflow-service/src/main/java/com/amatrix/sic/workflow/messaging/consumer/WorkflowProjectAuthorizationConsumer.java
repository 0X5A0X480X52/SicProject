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
 * 消费项目授权事件；对 PROJECT_LEADER_CHANGED 更新项目负责人快照和当前未完成负责人任务。
 */
@Component
public class WorkflowProjectAuthorizationConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final ProjectSnapshotMapper snapshots;

    public WorkflowProjectAuthorizationConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectSnapshotMapper snapshots) {
        this.mapper = mapper; this.guard = guard; this.snapshots = snapshots;
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.PROJECT_AUTHORIZATION_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        if (!guard.begin(event.eventId(), "workflow-project-authorization")) return;
        if (!"PROJECT_LEADER_CHANGED".equals(event.eventType())
                && !"PROJECT_DEPARTMENT_ASSIGNED".equals(event.eventType())) return;
        Long projectId = number(event.payload().get("projectId"));
        Long ownerId = number(event.payload().get("ownerUserId"));
        Long version = number(event.payload().get("projectVersion"));
        if (projectId == null || ownerId == null || version == null)
            throw new IllegalArgumentException("Project leader event fields are missing");
        String ownerName = string(event.payload().get("ownerName"));
        Long deptId = number(event.payload().get("deptId"));
        snapshots.owner(projectId,ownerId,ownerName,deptId,version,true);
        snapshots.leaderTasks(projectId,ownerId);
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
}
