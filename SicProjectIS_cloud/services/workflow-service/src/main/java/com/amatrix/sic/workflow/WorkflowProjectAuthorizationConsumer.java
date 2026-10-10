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
public class WorkflowProjectAuthorizationConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final JdbcTemplate jdbc;

    public WorkflowProjectAuthorizationConsumer(ObjectMapper mapper, ConsumedEventGuard guard, JdbcTemplate jdbc) {
        this.mapper = mapper; this.guard = guard; this.jdbc = jdbc;
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.PROJECT_AUTHORIZATION_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        if (!guard.begin(event.eventId(), "workflow-project-authorization")) return;
        if (!"PROJECT_LEADER_CHANGED".equals(event.eventType())) return;
        Long projectId = number(event.payload().get("projectId"));
        Long ownerId = number(event.payload().get("ownerUserId"));
        Long version = number(event.payload().get("projectVersion"));
        if (projectId == null || ownerId == null || version == null)
            throw new IllegalArgumentException("Project leader event fields are missing");
        String ownerName = string(event.payload().get("ownerName"));
        Long deptId = number(event.payload().get("deptId"));
        jdbc.update("""
                UPDATE project_snapshot SET owner_user_id=?,owner_name_snapshot=?,dept_id=?,source_version=?,
                  updated_at=CURRENT_TIMESTAMP(6) WHERE project_id=? AND source_version<=?
                """, ownerId, ownerName, deptId, version, projectId, version);
        jdbc.update("""
                UPDATE workflow_task t JOIN module_instance m ON m.id=t.module_instance_id
                   SET t.assignee_user_id=?
                 WHERE m.project_id=? AND m.finished=FALSE AND t.status='OPEN'
                   AND t.node_id=m.current_node_id AND t.candidate_role='PROJECT_LEADER'
                """, ownerId, projectId);
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
}
