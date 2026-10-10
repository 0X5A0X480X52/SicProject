package com.amatrix.sic.workflow;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.UserSummary;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkflowUserSnapshotConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final WorkflowServiceTokenClient iam;
    private final WorkflowInternalTokenProvider tokens;
    private final JdbcTemplate jdbc;

    public WorkflowUserSnapshotConsumer(ObjectMapper mapper, ConsumedEventGuard guard,
            WorkflowServiceTokenClient iam, WorkflowInternalTokenProvider tokens, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.guard = guard;
        this.iam = iam;
        this.tokens = tokens;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.IAM_QUEUE)
    @Transactional
    public void synchronize(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        if (!guard.begin(event.eventId(), "workflow-user-snapshot")) return;
        for (Long userId : affectedUsers(event.payload())) {
            refresh(userId);
        }
    }

    private void refresh(Long userId) {
        var response = iam.summary(tokens.authorization(), userId);
        if (!response.success() || response.data() == null) {
            throw new IllegalStateException("IAM user summary is unavailable: " + userId);
        }
        UserSummary user = response.data();
        jdbc.update("""
                INSERT INTO user_snapshot(user_id,username,real_name,enabled,source_version)
                VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE username=VALUES(username),real_name=VALUES(real_name),
                  enabled=VALUES(enabled),source_version=VALUES(source_version)
                """, user.userId(), user.username(), user.realName(), user.enabled(), user.authVersion());
    }

    private static Set<Long> affectedUsers(Map<String, Object> payload) {
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
}
