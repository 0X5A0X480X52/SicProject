package com.amatrix.sic.project;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectUserSnapshotConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final ProjectUserDirectory users;
    private final JdbcTemplate jdbc;

    public ProjectUserSnapshotConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectUserDirectory users,
            JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.guard = guard;
        this.users = users;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = ProjectSnapshotMessagingConfiguration.IAM_QUEUE)
    @Transactional
    public void synchronize(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        if (!guard.begin(event.eventId(), "project-user-snapshot")) return;
        for (Long userId : affectedUsers(event.payload())) users.refresh(userId);
        audit(event);
    }

    static Set<Long> affectedUsers(Map<String, Object> payload) {
        Set<Long> result = new LinkedHashSet<>();
        add(result, payload.get("userId"));
        Object affected = payload.get("affectedUsers");
        if (affected instanceof Collection<?> values) values.forEach(value -> add(result, value));
        return result;
    }

    private static void add(Set<Long> result, Object value) {
        if (value instanceof Number number) result.add(number.longValue());
        else if (value != null) {
            try { result.add(Long.valueOf(String.valueOf(value))); } catch (NumberFormatException ignored) {}
        }
    }

    private void audit(EventEnvelope<Map<String, Object>> event) throws Exception {
        Long targetId = asLong(event.payload().get("userId"));
        String operatorName = userName(event.actorUserId());
        String targetName = userName(targetId);
        jdbc.update("""
                INSERT INTO admin_operation_log(id,scope_type,action_type,operator_user_id,operator_name_snapshot,
                  target_user_id,target_name_snapshot,role_code,before_snapshot,after_snapshot,remark)
                VALUES (?,'IAM',?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=VALUES(id)
                """, event.eventId().toString(), event.eventType(), event.actorUserId(), operatorName, targetId,
                targetName, text(event.payload().get("roleCode")), json(event.payload().get("before")),
                json(event.payload().get("after")), text(event.payload().get("operation")));
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        return jdbc.query("SELECT real_name FROM user_snapshot WHERE user_id=?", (rs, row) -> rs.getString(1), userId)
                .stream().findFirst().orElse("user-" + userId);
    }

    private String json(Object value) throws Exception {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }
}
