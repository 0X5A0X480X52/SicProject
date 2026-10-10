package com.amatrix.sic.project.messaging.consumer;

import com.amatrix.sic.project.config.ProjectSnapshotMessagingConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
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
    private final ProjectAuthorizationMapper grants;

    public ProjectExpertAccessEndedConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectAuthorizationMapper grants) {
        this.mapper = mapper;
        this.guard = guard;
        this.grants = grants;
    }

    @RabbitListener(queues = ProjectSnapshotMessagingConfiguration.EXPERT_ACCESS_ENDED_QUEUE)
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
        // Keep the same project -> grant order as synchronous authorization writes.
        grants.lockProject(projectId);
        int changed=grants.endExpertAccess(projectId,moduleType,roundNo,taskNodeId,expertUserIds);
        if(changed>0)grants.touch(projectId);
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
