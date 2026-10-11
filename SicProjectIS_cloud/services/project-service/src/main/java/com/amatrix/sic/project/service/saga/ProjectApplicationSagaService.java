package com.amatrix.sic.project.service.saga;

import com.amatrix.sic.project.dto.ProjectDtos;
import com.amatrix.sic.project.client.WorkflowSagaClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.project.dao.ApplicationSagaMapper;
import com.amatrix.sic.project.entity.ApplicationSagaRow;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationRequest;
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationResponse;

import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectApplicationSagaService {
    private final ApplicationSagaMapper mapper;
    private final TransactionTemplate transactions;
    private final WorkflowSagaClient workflow;
    private final InternalTokenProvider serviceTokens;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;
    private final com.amatrix.sic.project.service.ProjectDepartmentService departments;
    private final com.amatrix.sic.project.service.directory.ProjectUserDirectory users;

    public ProjectApplicationSagaService(ApplicationSagaMapper mapper, TransactionTemplate transactions,
            WorkflowSagaClient workflow, InternalTokenProvider serviceTokens, OutboxService outbox,
            ObjectMapper objectMapper, com.amatrix.sic.project.service.ProjectDepartmentService departments,
            com.amatrix.sic.project.service.directory.ProjectUserDirectory users) {
        this.mapper = mapper;
        this.transactions = transactions;
        this.workflow = workflow;
        this.serviceTokens = serviceTokens;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.departments = departments;
        this.users = users;
    }

    public StartProjectApplicationResponse start(Long actorId, String actorName, Long deptId, String deptName,
            String userAuthorization, StartProjectApplicationRequest request) {
        validate(request);
        // Read authoritative IAM data before any project/Saga writes or transaction is opened.
        var applicant = users.fetchAll(List.of(actorId)).getFirst();
        if (!applicant.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
        var department = departments.requireEnabled(applicant.deptId());
        String requestHash = hash(request);
        ApplicationSagaRow existing = findRecent(actorId, requestHash);
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
        Long projectId = transactions.execute(status -> createProject(sagaId, actorId, actorName, department.deptId(), department.deptName(),
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
            transactions.executeWithoutResult(status -> mapper.workflowCreated(sagaId.toString(),result.moduleInstanceId(),write(response)));
            transactions.executeWithoutResult(status -> confirm(sagaId, projectId, actorId, response));
            return response;
        } catch (Exception ex) {
            mapper.confirmationFailed(sagaId.toString(),trim(ex.getMessage()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Workflow was created; project confirmation will be recovered automatically", ex);
        }
    }

    @Scheduled(fixedDelayString = "${sic.saga.recovery-delay-ms:30000}")
    public void recoverIncompleteConfirmations() {
        List<String> sagaIds=mapper.recoverable();
        for (String raw : sagaIds) {
            try {
                UUID sagaId = UUID.fromString(raw);
                var status = workflow.status(serviceTokens.authorization(), sagaId);
                if (status.success() && status.data() != null && status.data().moduleInstanceId() != null) {
                    ApplicationSagaRow saga = findById(sagaId);
                    StartProjectApplicationResponse response = saga.responseJson() == null
                            ? new StartProjectApplicationResponse(saga.projectId(), status.data().moduleInstanceId(),
                                    status.data().transition())
                            : read(saga.responseJson(), StartProjectApplicationResponse.class);
                    transactions.executeWithoutResult(tx -> confirm(sagaId, saga.projectId(), saga.actorUserId(), response));
                }
            } catch (Exception ex) {
                mapper.retry(raw,trim(ex.getMessage()));
            }
        }
    }

    private Long createProject(UUID sagaId, Long actorId, String actorName, Long deptId, String deptName,
            String requestHash, String code, StartProjectApplicationRequest request) {
        java.util.Map<String,Object> project=new java.util.LinkedHashMap<>();
        project.put("code",code);project.put("name",request.projectName().trim());project.put("type",request.projectType());
        project.put("level",request.projectLevel());project.put("amount",request.approvedAmount());
        project.put("start",request.startDate());project.put("end",request.endDate());project.put("actor",actorId);
        project.put("actorName",textOr(actorName,"user-"+actorId));project.put("department",deptId);project.put("departmentName",deptName);
        project.put("sagaId",sagaId.toString());project.put("hash",requestHash);
        mapper.insertProject(project);mapper.insertOwner(project);mapper.insertSaga(project);
        return ((Number)java.util.Objects.requireNonNull(project.get("id"))).longValue();
    }

    private void confirm(UUID sagaId, Long projectId, Long actorId, StartProjectApplicationResponse response) {
        mapper.activateProject(projectId);mapper.complete(sagaId.toString(),write(response));
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
            mapper.failProject(projectId);mapper.compensate(sagaId.toString(),trim(cause.getMessage()));
        });
    }

    private ApplicationSagaRow findRecent(Long actorId, String requestHash) {
        return mapper.recent(actorId,requestHash,Instant.now().minusSeconds(300));
    }

    private ApplicationSagaRow findById(UUID id) {
        var saga=mapper.saga(id.toString());
        if(saga==null)throw new IllegalStateException("Saga disappeared: "+id);
        return saga;
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

}
