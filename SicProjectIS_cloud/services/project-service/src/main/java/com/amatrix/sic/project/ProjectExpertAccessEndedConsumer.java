package com.amatrix.sic.project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
public class ProjectExpertAccessEndedConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final JdbcTemplate jdbc;

    public ProjectExpertAccessEndedConsumer(ObjectMapper mapper, ConsumedEventGuard guard, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = ProjectSnapshotMessagingConfiguration.EXPERT_ACCESS_ENDED_QUEUE)
    @Transactional
    public void consume(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        if (!"EXPERT_ACCESS_ENDED".equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected event type on expert access queue");
        }
        if (!guard.begin(event.eventId(), "project-expert-access-ended")) return;
        Map<String, Object> payload = event.payload();
        Long projectId = asLong(payload.get("projectId"));
        Integer roundNo = asInteger(payload.get("roundNo"));
        String moduleType = text(payload.get("moduleType"));
        String taskNodeId = text(payload.get("taskNodeId"));
        List<Long> expertUserIds = asLongs(payload.get("expertUserIds"));
        if (projectId == null || roundNo == null || moduleType == null
                || (taskNodeId == null && expertUserIds.isEmpty())) {
            throw new IllegalArgumentException("Expert access event is incomplete");
        }
        int changed;
        if (taskNodeId != null) {
            changed = jdbc.update("""
                    UPDATE project_access_grant SET status='REVOKED',effective_to=CURRENT_TIMESTAMP(6)
                     WHERE project_id=? AND grant_role_code='PROJECT_MODULE_EXPERT_ASSIGNMENT' AND status='ACTIVE'
                       AND module_type=? AND round_no=? AND task_node_id=?
                    """, projectId, moduleType, roundNo, taskNodeId);
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(expertUserIds.size(), "?"));
            List<Object> arguments = new ArrayList<>();
            arguments.add(projectId);
            arguments.add(moduleType);
            arguments.add(roundNo);
            arguments.addAll(expertUserIds);
            changed = jdbc.update("""
                    UPDATE project_access_grant SET status='REVOKED',effective_to=CURRENT_TIMESTAMP(6)
                     WHERE project_id=? AND grant_role_code='PROJECT_MODULE_EXPERT_ASSIGNMENT' AND status='ACTIVE'
                       AND module_type=? AND round_no=? AND grantee_user_id IN (%s)
                    """.formatted(placeholders), arguments.toArray());
        }
        if (changed > 0) {
            jdbc.update("UPDATE project SET version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", projectId);
        }
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) return null;
        return String.valueOf(value);
    }

    private static Long asLong(Object value) {
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ex) { return null; }
    }

    private static Integer asInteger(Object value) {
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ex) { return null; }
    }

    private static List<Long> asLongs(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        List<Long> result = new ArrayList<>();
        for (Object item : values) {
            Long parsed = asLong(item);
            if (parsed != null && !result.contains(parsed)) result.add(parsed);
        }
        return result;
    }
}
