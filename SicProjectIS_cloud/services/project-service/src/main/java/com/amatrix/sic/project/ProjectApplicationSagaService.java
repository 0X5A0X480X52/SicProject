package com.amatrix.sic.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.project.ProjectDtos.StartProjectApplicationRequest;
import com.amatrix.sic.project.ProjectDtos.StartProjectApplicationResponse;

import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectApplicationSagaService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WorkflowSagaClient workflow;
    private final InternalTokenProvider serviceTokens;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public ProjectApplicationSagaService(JdbcTemplate jdbc, TransactionTemplate transactions,
            WorkflowSagaClient workflow, InternalTokenProvider serviceTokens, OutboxService outbox,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.workflow = workflow;
        this.serviceTokens = serviceTokens;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public StartProjectApplicationResponse start(Long actorId, String actorName, Long deptId, String deptName,
            String userAuthorization, StartProjectApplicationRequest request) {
        validate(request);
        String requestHash = hash(request);
        ExistingSaga existing = findRecent(actorId, requestHash);
        if (existing != null) {
            if ("COMPLETED".equals(existing.status()) && existing.responseJson() != null) {
                return read(existing.responseJson(), StartProjectApplicationResponse.class);
            }
            if (!"FAILED".equals(existing.status()) && !"COMPENSATED".equals(existing.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An identical project application is already in progress");
            }
        }

        UUID sagaId = UUID.randomUUID();
        String projectCode = projectCode(request);
        Long projectId = transactions.execute(status -> createProject(sagaId, actorId, actorName, deptId, deptName,
                requestHash, projectCode, request));
        ApplicationSagaCommand command = new ApplicationSagaCommand(sagaId, projectId, actorId,
                projectCode, request.projectName().trim(), request.projectType(), request.projectLevel(),
                request.approvedAmount(), request.startDate(), request.endDate(),
                textOr(request.applicationTitle(), request.projectName()), Boolean.TRUE.equals(request.isLimitedProject()),
                request.applicationSummary());
        ApplicationSagaResult result;
        try {
            var remote = workflow.create(serviceTokens.authorization(), userAuthorization, command);
            if (!remote.success() || remote.data() == null) {
                throw new IllegalStateException(remote.message());
            }
            result = remote.data();
        } catch (Exception ex) {
            compensate(sagaId, projectId, ex);
            if (ex instanceof ResponseStatusException responseStatus) throw responseStatus;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to start project workflow; the provisional project was compensated", ex);
        }
        StartProjectApplicationResponse response = new StartProjectApplicationResponse(projectId,
                result.moduleInstanceId(), result.transition());
        try {
            transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE application_saga SET status='WORKFLOW_CREATED',module_instance_id=?,response_json=?,
                           next_retry_at=CURRENT_TIMESTAMP(6) WHERE saga_id=?
                    """, result.moduleInstanceId(), write(response), sagaId.toString()));
            transactions.executeWithoutResult(status -> confirm(sagaId, projectId, actorId, response));
            return response;
        } catch (Exception ex) {
            jdbc.update("UPDATE application_saga SET status='WORKFLOW_CREATED',last_error=?,next_retry_at=CURRENT_TIMESTAMP(6) WHERE saga_id=?",
                    trim(ex.getMessage()), sagaId.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Workflow was created; project confirmation will be recovered automatically", ex);
        }
    }

    @Scheduled(fixedDelayString = "${sic.saga.recovery-delay-ms:30000}")
    public void recoverIncompleteConfirmations() {
        List<String> sagaIds = jdbc.query("""
                SELECT saga_id FROM application_saga WHERE status='WORKFLOW_CREATED'
                 AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 20
                """, (rs, row) -> rs.getString(1));
        for (String raw : sagaIds) {
            try {
                UUID sagaId = UUID.fromString(raw);
                var status = workflow.status(serviceTokens.authorization(), sagaId);
                if (status.success() && status.data() != null && status.data().moduleInstanceId() != null) {
                    ExistingSaga saga = findById(sagaId);
                    StartProjectApplicationResponse response = saga.responseJson() == null
                            ? new StartProjectApplicationResponse(saga.projectId(), status.data().moduleInstanceId(),
                                    status.data().transition())
                            : read(saga.responseJson(), StartProjectApplicationResponse.class);
                    transactions.executeWithoutResult(tx -> confirm(sagaId, saga.projectId(), saga.actorUserId(), response));
                }
            } catch (Exception ex) {
                jdbc.update("""
                        UPDATE application_saga SET retry_count=retry_count+1,last_error=?,
                               next_retry_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL LEAST(300, POW(2,LEAST(retry_count,8))) SECOND)
                         WHERE saga_id=?
                        """, trim(ex.getMessage()), raw);
            }
        }
    }

    private Long createProject(UUID sagaId, Long actorId, String actorName, Long deptId, String deptName,
            String requestHash, String code, StartProjectApplicationRequest request) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO project(project_code,project_name,project_type,project_level,approved_amount,
                      start_date,end_date,owner_user_id,owner_name_snapshot,dept_id,dept_name_snapshot,lifecycle_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'PROVISIONING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, code); statement.setString(2, request.projectName().trim());
            statement.setString(3, request.projectType()); statement.setString(4, request.projectLevel());
            statement.setBigDecimal(5, request.approvedAmount());
            setDate(statement, 6, request.startDate()); setDate(statement, 7, request.endDate());
            statement.setLong(8, actorId); statement.setString(9, textOr(actorName, "user-" + actorId));
            if (deptId == null) statement.setNull(10, java.sql.Types.BIGINT); else statement.setLong(10, deptId);
            statement.setString(11, deptName); return statement;
        }, holder);
        Long projectId = java.util.Objects.requireNonNull(holder.getKey()).longValue();
        jdbc.update("""
                INSERT INTO project_member(project_id,user_id,member_role,responsibility,user_name_snapshot)
                VALUES (?,?,'LEADER','Project owner',?)
                """, projectId, actorId, textOr(actorName, "user-" + actorId));
        jdbc.update("""
                INSERT INTO application_saga(saga_id,project_id,actor_user_id,request_hash,status)
                VALUES (?,?,?,?,'PROJECT_CREATED')
                """, sagaId.toString(), projectId, actorId, requestHash);
        return projectId;
    }

    private void confirm(UUID sagaId, Long projectId, Long actorId, StartProjectApplicationResponse response) {
        jdbc.update("UPDATE project SET lifecycle_status='ACTIVE',version=version+1 WHERE id=?", projectId);
        jdbc.update("UPDATE application_saga SET status='COMPLETED',response_json=?,last_error=NULL WHERE saga_id=?",
                write(response), sagaId.toString());
        outbox.enqueue("project.created", EventEnvelope.create("PROJECT_APPLICATION_STARTED", "PROJECT",
                projectId.toString(), sagaId.toString(), null, actorId,
                java.util.Map.of("projectId", projectId, "moduleInstanceId", response.moduleInstanceId(),
                        "sagaId", sagaId.toString())));
    }

    private void compensate(UUID sagaId, Long projectId, Exception cause) {
        try {
            workflow.compensate(serviceTokens.authorization(), sagaId);
        } catch (Exception ignored) {
            // Workflow compensation is idempotent; recovery can safely repeat it.
        }
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE project SET lifecycle_status='FAILED',version=version+1 WHERE id=?", projectId);
            jdbc.update("UPDATE application_saga SET status='COMPENSATED',last_error=? WHERE saga_id=?",
                    trim(cause.getMessage()), sagaId.toString());
        });
    }

    private ExistingSaga findRecent(Long actorId, String requestHash) {
        List<ExistingSaga> rows = jdbc.query("""
                SELECT saga_id,project_id,actor_user_id,status,response_json FROM application_saga
                 WHERE actor_user_id=? AND request_hash=? AND created_at>=?
                 ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> new ExistingSaga(UUID.fromString(rs.getString("saga_id")), rs.getLong("project_id"),
                        rs.getLong("actor_user_id"), rs.getString("status"), rs.getString("response_json")),
                actorId, requestHash, Timestamp.from(Instant.now().minusSeconds(300)));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ExistingSaga findById(UUID id) {
        List<ExistingSaga> rows = jdbc.query("""
                SELECT saga_id,project_id,actor_user_id,status,response_json FROM application_saga WHERE saga_id=?
                """, (rs, row) -> new ExistingSaga(id, rs.getLong("project_id"), rs.getLong("actor_user_id"),
                        rs.getString("status"), rs.getString("response_json")), id.toString());
        if (rows.isEmpty()) throw new IllegalStateException("Saga disappeared: " + id);
        return rows.getFirst();
    }

    private String hash(StartProjectApplicationRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static void validate(StartProjectApplicationRequest request) {
        if (request == null || request.projectName() == null || request.projectName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
    }

    private static String projectCode(StartProjectApplicationRequest request) {
        return textOr(request.projectCode(), "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trim(String value) {
        if (value == null) return null; return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.DATE); else statement.setObject(index, value);
    }

    private record ExistingSaga(UUID sagaId, Long projectId, Long actorUserId, String status, String responseJson) {
    }
}
