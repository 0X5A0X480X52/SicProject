package com.amatrix.sic.workflow.messaging.consumer;

import com.amatrix.sic.workflow.client.WorkflowServiceTokenClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;
import com.amatrix.sic.workflow.config.WorkflowSnapshotMessagingConfiguration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.amatrix.sic.workflow.dao.WorkflowUserSnapshotMapper;
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
    private final WorkflowUserSnapshotMapper snapshots;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public WorkflowUserSnapshotConsumer(ObjectMapper mapper, ConsumedEventGuard guard,
            WorkflowServiceTokenClient iam, WorkflowInternalTokenProvider tokens, WorkflowUserSnapshotMapper snapshots,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.guard = guard;
        this.iam = iam;
        this.tokens = tokens;
        this.snapshots = snapshots;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @RabbitListener(queues = WorkflowSnapshotMessagingConfiguration.IAM_QUEUE)
    public void synchronize(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        java.util.List<Long> ids = affectedUsers(event.payload()).stream().toList();
        java.util.List<UserSummary> users = new java.util.ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += 100) {
            var chunk = ids.subList(offset, Math.min(ids.size(), offset + 100));
            var response = iam.summaries(tokens.authorization(), chunk);
            if (!response.success() || response.data() == null
                    || !response.data().stream().map(UserSummary::userId).collect(java.util.stream.Collectors.toSet())
                            .equals(new java.util.HashSet<>(chunk)))
                throw new IllegalStateException("IAM user summaries are incomplete");
            users.addAll(response.data());
        }
        transactions.executeWithoutResult(status -> {
            if (!guard.begin(event.eventId(), "workflow-user-snapshot")) return;
            users.forEach(this::persist);
        });
    }

    private void persist(UserSummary user) {
        snapshots.upsert(user);
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
