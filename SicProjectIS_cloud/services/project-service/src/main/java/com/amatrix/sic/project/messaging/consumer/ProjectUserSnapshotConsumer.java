package com.amatrix.sic.project.messaging.consumer;

import com.amatrix.sic.project.service.directory.ProjectUserDirectory;
import com.amatrix.sic.project.config.ProjectSnapshotMessagingConfiguration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.amatrix.sic.project.dao.ProjectAuditMapper;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.ConsumedEventGuard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectUserSnapshotConsumer {
    private final ObjectMapper mapper;
    private final ConsumedEventGuard guard;
    private final ProjectUserDirectory users;
    private final ProjectAuditMapper auditMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public ProjectUserSnapshotConsumer(ObjectMapper mapper, ConsumedEventGuard guard, ProjectUserDirectory users,
            ProjectAuditMapper auditMapper, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.guard = guard;
        this.users = users;
        this.auditMapper = auditMapper;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @RabbitListener(queues = ProjectSnapshotMessagingConfiguration.IAM_QUEUE)
    public void synchronize(String json) throws Exception {
        EventEnvelope<Map<String, Object>> event = mapper.readValue(json, new TypeReference<>() {});
        var summaries = users.fetchAll(affectedUsers(event.payload()));
        transactions.executeWithoutResult(status -> {
            if (!guard.begin(event.eventId(), "project-user-snapshot")) return;
            summaries.forEach(users::upsert);
            try { audit(event); }
            catch (Exception ex) { throw new IllegalStateException("Unable to persist IAM audit", ex); }
        });
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
        Map<String,Object> log=new java.util.LinkedHashMap<>();
        log.put("id",event.eventId().toString());log.put("action",event.eventType());
        log.put("operatorId",event.actorUserId());log.put("operatorName",operatorName);
        log.put("targetId",targetId);log.put("targetName",targetName);log.put("role",text(event.payload().get("roleCode")));
        log.put("before",json(event.payload().get("before")));log.put("after",json(event.payload().get("after")));
        log.put("remark",text(event.payload().get("operation")));auditMapper.insertIam(log);
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        String name=auditMapper.userName(userId);
        return name==null?"user-"+userId:name;
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
