package com.amatrix.sic.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.security.CanOperateModule;
import com.amatrix.sic.workflow.WorkflowBpmnParseResult.MaterialRequirementConfig;
import com.amatrix.sic.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sic.workflow.WorkflowDefinitionNavigator.ResolvedTarget;
import com.amatrix.sic.workflow.WorkflowDtos.TransitionRequest;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WorkflowRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeService.class);
    private static final List<String> POSITIVE_RESULTS = List.of(
            "APPROVED", "PASS", "PASSED", "RECOMMENDED", "ACCEPTED", "PROCESS_COMPLETED", "SCHOOL_LEVEL",
            "TRUE");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ProjectAccessClient projectAccess;
    private final MaterialReservationClient materials;
    private final WorkflowInternalTokenProvider tokens;
    private final WorkflowOperationPolicy operationPolicy;
    private final WorkflowDefinitionCatalog definitions;
    private final WorkflowDefinitionNavigator navigator;
    private final WorkflowTransitionActionService actions;
    private final NodeFormService nodeForms;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;
    private final WorkflowProjectAccessPolicy workflowAccess;
    private final ModuleSuccessorPolicy successors;

    public WorkflowRuntimeService(JdbcTemplate jdbc, TransactionTemplate transactions, ProjectAccessClient projectAccess,
            MaterialReservationClient materials, WorkflowInternalTokenProvider tokens,
            WorkflowOperationPolicy operationPolicy, WorkflowDefinitionCatalog definitions,
            WorkflowDefinitionNavigator navigator, WorkflowTransitionActionService actions, NodeFormService nodeForms,
            OutboxService outbox, ObjectMapper objectMapper, WorkflowProjectAccessPolicy workflowAccess,
            ModuleSuccessorPolicy successors) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.projectAccess = projectAccess;
        this.materials = materials;
        this.tokens = tokens;
        this.operationPolicy = operationPolicy;
        this.definitions = definitions;
        this.navigator = navigator;
        this.actions = actions;
        this.nodeForms = nodeForms;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.workflowAccess = workflowAccess;
        this.successors = successors;
    }

    @PreAuthorize("isAuthenticated()")
    public StateTransitionContract start(Long projectId, String moduleType, ActorContext actor) {
        String type = normalizeModule(moduleType);
        if ("APPLICATION".equals(type)) {
            throw bad("APPLICATION must be started with POST /api/project-applications/start");
        }
        if (!actor.hasRole("SCIENCE_ADMIN") && !actor.hasRole("SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only science office administrators can start workflow modules");
        }
        var decision = projectAccess.decide(tokens.authorization(), projectId, actor.userId(), actor.deptId(),
                "START_" + type, actor.roleCodes());
        if (!decision.success() || decision.data() == null || !decision.data().allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
        WorkflowDefinitionCatalog.ActiveDefinition definition = definitions.active(type);
        return transactions.execute(status -> {
            if (findModuleId(projectId, type) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, type + " module already exists for this project");
            }
            return createModule(projectId, type, actor, definition);
        });
    }

    @CanOperateModule
    public StateTransitionContract transition(Long moduleInstanceId, ActorContext actor, TransitionRequest request) {
        if (request == null || blank(request.eventType())) {
            throw bad("Event type is required");
        }
        ModuleRecord snapshot = module(moduleInstanceId, false);
        if (snapshot.finished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module instance is already finished");
        }
        int expected = request.expectedSeq() == null ? snapshot.seq() : request.expectedSeq();
        WorkflowBpmnParseResult model = definitions.model(snapshot.definitionId());
        NodeConfig currentNode = node(model, snapshot.nodeId());
        List<String> requiredTypes = requiredMaterialTypes(currentNode);
        MaterialReservationResult reservation = null;
        List<Long> materialIds = request.materialVersionIds() == null ? List.of() : request.materialVersionIds();
        if (!materialIds.isEmpty() || !requiredTypes.isEmpty()) {
            String commandId = moduleInstanceId + ":" + expected + ":" + request.eventType();
            var response = materials.reserve(tokens.authorization(), new MaterialReservationCommand(commandId,
                    actor.userId(), snapshot.projectId(), moduleInstanceId, expected, materialIds, requiredTypes));
            if (!response.success() || response.data() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        response.message() == null ? "Material reservation failed" : response.message());
            }
            reservation = response.data();
        }
        MaterialReservationResult finalReservation = reservation;
        try {
            return Objects.requireNonNull(transactions.execute(
                    status -> lockedTransition(moduleInstanceId, actor, request, expected, finalReservation, true)));
        } catch (RuntimeException ex) {
            if (reservation != null) {
                try {
                    materials.release(tokens.authorization(), reservation.reservationId());
                } catch (Exception ignored) {
                    // Reservation cleanup is also covered by the material-service expiry job.
                }
            }
            throw ex;
        }
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> runtimeView(Long moduleInstanceId, ActorContext actor) {
        ModuleRecord module = module(moduleInstanceId, false);
        if (!workflowAccess.canAccessModule(actor, moduleInstanceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
        WorkflowBpmnParseResult model = definitions.model(module.definitionId());
        NodeConfig currentNode = node(model, module.nodeId());
        Long currentWorkflowNodeId = jdbc.query("""
                SELECT id FROM workflow_node_config WHERE workflow_definition_id=? AND node_id=?
                """, (rs, row) -> rs.getLong(1), module.definitionId(), module.nodeId()).stream().findFirst().orElse(null);
        boolean canOperate = operationPolicy.exactCanOperate(actor, moduleInstanceId);
        List<Map<String, Object>> history = jdbc.query(
                "SELECT * FROM state_record WHERE module_instance_id=? ORDER BY seq",
                (rs, row) -> stateMap(rs), moduleInstanceId);
        List<Map<String, Object>> tasks = jdbc.query("""
                SELECT * FROM workflow_task WHERE module_instance_id=? AND status='OPEN' ORDER BY id
                """, (rs, row) -> linked("taskInstanceId", rs.getLong("id"), "moduleInstanceId", moduleInstanceId,
                        "nodeId", rs.getString("node_id"), "stateCode", module.state(),
                        "assigneeUserId", nullableLong(rs, "assignee_user_id"),
                        "candidateRoleCode", rs.getString("candidate_role"), "taskStatus", rs.getString("status"),
                        "roundNo", rs.getInt("round_no"), "createdAt", rs.getObject("created_at", LocalDateTime.class),
                        "completedAt", rs.getObject("completed_at", LocalDateTime.class)), moduleInstanceId);
        Map<String, Object> context = linked("moduleInstanceId", module.id(), "projectId", module.projectId(),
                "moduleType", module.type(), "workflowDefinitionId", module.definitionId(), "currentSeq", module.seq(),
                "currentRoundNo", module.round(), "currentState", module.state(), "currentNodeId", module.nodeId(),
                "currentWorkflowNodeId", currentWorkflowNodeId,
                "currentNodeName", currentNode.nodeName(), "currentNodeType", currentNode.nodeType(),
                "currentCandidateRoleCode", currentNode.candidateRoleCode(), "startedAt", module.createdAt(),
                "finishedAt", module.finished() ? module.updatedAt() : null);
        List<Map<String, Object>> transitions = module.finished() ? List.of()
                : navigator.outgoing(model, module.nodeId()).stream().map(this::transitionMap).toList();
        List<Map<String, Object>> requirements = currentNode.materialRequirements().stream()
                .map(this::requirementMap).toList();
        List<Map<String, Object>> forms = nodeForms.forNode(module.type(), module.nodeId(), module.state());
        if (forms.isEmpty()) forms = List.of(defaultForm(module, currentNode));
        return linked("context", context, "canOperate", canOperate, "availableTransitions", transitions,
                "materialRequirements", requirements, "nodeForms", forms,
                "openTasks", tasks, "history", history);
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> workbench(ActorContext actor) {
        List<Map<String, Object>> candidates = jdbc.query("""
                SELECT DISTINCT m.*,p.project_code,p.project_name,p.lifecycle_stage
                  FROM module_instance m LEFT JOIN project_snapshot p ON p.project_id=m.project_id
                 WHERE m.finished=FALSE AND EXISTS (
                   SELECT 1 FROM workflow_task t WHERE t.module_instance_id=m.id
                     AND t.node_id=m.current_node_id AND t.status='OPEN')
                 ORDER BY m.updated_at DESC
                """, (rs, row) -> linked("moduleInstanceId", rs.getLong("id"), "projectId",
                rs.getLong("project_id"), "projectCode", rs.getString("project_code"), "projectName",
                rs.getString("project_name"), "moduleType", rs.getString("module_code"), "lifecycleStage",
                rs.getString("lifecycle_stage"), "workflowDefinitionId", rs.getLong("definition_id"), "currentState",
                rs.getString("current_state"), "currentNodeId", rs.getString("current_node_id"), "currentNodeName",
                rs.getString("current_node_id"), "currentSeq", rs.getInt("current_seq"), "currentRoundNo",
                rs.getInt("current_round"), "lastTransitionTime", rs.getObject("updated_at", LocalDateTime.class),
                "finished", rs.getBoolean("finished"), "todo", true, "canOperate", true));
        List<Long> ids = candidates.stream().map(row -> (Long) row.get("moduleInstanceId")).toList();
        java.util.Set<Long> allowed = operationPolicy.operableModuleIds(actor, ids);
        return candidates.stream().filter(row -> allowed.contains(row.get("moduleInstanceId"))).toList();
    }

    private StateTransitionContract createModule(Long projectId, String type, ActorContext actor,
            WorkflowDefinitionCatalog.ActiveDefinition definition) {
        ResolvedTarget resolved = navigator.start(definition.model(), WorkflowRuntimeService::unconditional);
        TransitionConfig startTransition = resolved.initialTransition();
        NodeConfig target = resolved.node();
        GeneratedKeyHolder moduleHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO module_instance(project_id,started_by_user_id,module_code,definition_id,current_node_id,
                      current_state,current_seq,current_round,finished) VALUES (?,?,?,?,?,?,1,1,?)
                    """, new String[] { "id" });
            statement.setLong(1, projectId);
            statement.setLong(2, actor.userId());
            statement.setString(3, type);
            statement.setLong(4, definition.id());
            statement.setString(5, target.nodeId());
            statement.setString(6, target.stateCode());
            statement.setBoolean(7, "END_EVENT".equals(target.nodeType()));
            return statement;
        }, moduleHolder);
        Long moduleId = Objects.requireNonNull(moduleHolder.getKey()).longValue();
        StateRecordContract record = insertState(moduleId, 1, 1, startTransition.eventType(),
                startTransition.sourceStateCode(), target.stateCode(), startTransition.sourceRef(), target.nodeId(),
                startTransition.result(), type + " module started", actor);
        boolean finished = "END_EVENT".equals(target.nodeType());
        if (!finished) createTask(moduleId, projectId, target, actor, 1);
        stateEvent(projectId, moduleId, type, record, actor.userId(), null);
        return new StateTransitionContract(record, target.nodeId(), target.stateCode(), finished);
    }

    public void autoTransitionAfterExpertReviewComplete(Long batchId) {
        if (batchId == null) return;
        transactions.executeWithoutResult(status -> {
            ReviewBatch batch = reviewBatch(batchId);
            if (batch == null || !"COMPLETED".equals(batch.status())) return;
            ModuleRecord module = module(batch.moduleInstanceId(), true);
            if (module.finished() || batch.round() != module.round()) return;
            if (batch.workflowNodeId() != null) {
                String configuredNode = jdbc.query("SELECT node_id FROM workflow_node_config WHERE id=?",
                        (rs, row) -> rs.getString(1), batch.workflowNodeId()).stream().findFirst().orElse(null);
                if (configuredNode != null
                        && !Objects.equals(toReviewTaskNode(configuredNode), toReviewTaskNode(module.nodeId()))) return;
            }
            WorkflowBpmnParseResult model = definitions.model(module.definitionId());
            TransitionConfig transition = navigator.outgoing(model, module.nodeId()).stream()
                    .filter(item -> isExpertReviewSubmitEvent(item.eventType())).findFirst().orElse(null);
            if (transition == null) return;
            ActorContext system = new ActorContext(batch.createdBy(), "SYSTEM", null, null,
                    List.of("SYSTEM_ADMIN"), List.of(), "expert-review:" + batchId, 0);
            TransitionRequest request = new TransitionRequest(transition.eventType(), module.seq(),
                    transition.result(), "Expert review completed; workflow advanced automatically", List.of(), null,
                    null);
            lockedTransition(module.id(), system, request, module.seq(), null, false);
        });
    }

    @Scheduled(initialDelayString = "${sic.workflow.successor-recovery-initial-delay-ms:15000}",
            fixedDelayString = "${sic.workflow.successor-recovery-delay-ms:300000}")
    public void recoverMissingSuccessors() {
        List<Long> sourceIds = jdbc.query("""
                SELECT m.id FROM module_instance m
                 WHERE m.finished=TRUE
                   AND ((m.module_code='APPLICATION' AND m.current_state='APPLICATION_APPROVED')
                     OR (m.module_code='CONTRACT' AND m.current_state='CONTRACT_APPROVED'))
                   AND NOT EXISTS (
                     SELECT 1 FROM module_instance n
                      WHERE n.project_id=m.project_id
                        AND n.module_code=CASE m.module_code
                          WHEN 'APPLICATION' THEN 'CONTRACT' ELSE 'ACCEPTANCE' END)
                 ORDER BY m.id LIMIT 50
                """, (rs, row) -> rs.getLong(1));
        for (Long sourceId : sourceIds) {
            try {
                transactions.executeWithoutResult(status -> recoverSuccessor(sourceId));
            } catch (RuntimeException ex) {
                log.error("Unable to recover successor for completed module {}", sourceId, ex);
            }
        }
    }

    @Scheduled(initialDelayString = "${sic.workflow.assignment-recovery-initial-delay-ms:20000}",
            fixedDelayString = "${sic.workflow.assignment-recovery-delay-ms:300000}")
    public void reconcileProjectLeaderTasks() {
        record LeaderTask(long taskId, long projectId, String moduleType, int round, String nodeId) {}
        List<LeaderTask> tasksToCheck = jdbc.query("""
                SELECT t.id,m.project_id,m.module_code,t.round_no,t.node_id
                  FROM workflow_task t JOIN module_instance m ON m.id=t.module_instance_id
                 WHERE t.status='OPEN' AND t.candidate_role='PROJECT_LEADER'
                   AND t.node_id=m.current_node_id AND m.finished=FALSE
                 ORDER BY t.id LIMIT 200
                """, (rs, row) -> new LeaderTask(rs.getLong("id"), rs.getLong("project_id"),
                        rs.getString("module_code"), rs.getInt("round_no"), rs.getString("node_id")));
        if (tasksToCheck.isEmpty()) return;
        ActorContext system = new ActorContext(0L, "workflow-recovery", null, null,
                List.of("SYSTEM_ADMIN"), List.of(), "leader-task-recovery", 0);
        List<com.amatrix.sic.contracts.ProjectOperationTarget> targets = tasksToCheck.stream()
                .map(task -> new com.amatrix.sic.contracts.ProjectOperationTarget(String.valueOf(task.taskId()),
                        task.projectId(), "READ", task.moduleType(), task.round(), task.nodeId()))
                .toList();
        var response = projectAccess.decideBatch(tokens.authorization(),
                new com.amatrix.sic.contracts.ProjectAccessBatchRequest(system, targets));
        if (!response.success() || response.data() == null)
            throw new IllegalStateException("Unable to reconcile project leader tasks");
        transactions.executeWithoutResult(status -> response.data().forEach(context -> {
            if (context.ownerUserId() == null) return;
            jdbc.update("""
                    UPDATE project_snapshot SET owner_user_id=?,owner_name_snapshot=?,dept_id=?,source_version=?,
                      updated_at=CURRENT_TIMESTAMP(6) WHERE project_id=? AND source_version<=?
                    """, context.ownerUserId(), context.ownerName(), context.deptId(), context.projectVersion(),
                    context.projectId(), context.projectVersion());
            jdbc.update("""
                    UPDATE workflow_task SET assignee_user_id=? WHERE id=? AND status='OPEN'
                      AND candidate_role='PROJECT_LEADER'
                    """, context.ownerUserId(), Long.valueOf(context.decisionId()));
        }));
    }

    @Scheduled(initialDelayString = "${sic.workflow.expert-access-recovery-initial-delay-ms:25000}",
            fixedDelayString = "${sic.workflow.expert-access-recovery-delay-ms:300000}")
    public void recoverEndedExpertAccess() {
        List<Long> batchIds = jdbc.query("""
                SELECT b.id FROM expert_review_batch b JOIN module_instance m ON m.id=b.module_instance_id
                 LEFT JOIN workflow_node_config n ON n.id=b.workflow_node_id
                 WHERE b.access_ended_at IS NULL
                   AND (m.finished=TRUE OR b.round_no<>m.current_round
                     OR REPLACE(REPLACE(n.node_id,'ExpertAssignTask','ExpertReviewTask'),
                                'ExpertSummaryTask','ExpertReviewTask')<>m.current_node_id)
                 ORDER BY b.id LIMIT 100
                """, (rs, row) -> rs.getLong(1));
        for (Long batchId : batchIds) {
            try {
                transactions.executeWithoutResult(status -> endExpertAccessBatch(batchId, 0L));
            } catch (RuntimeException ex) {
                log.error("Unable to recover ended expert access for review batch {}", batchId, ex);
            }
        }
    }

    private void endExpertAccess(ModuleRecord module, String reviewNodeId, Long actorId) {
        List<Long> batchIds = jdbc.query("""
                SELECT b.id FROM expert_review_batch b LEFT JOIN workflow_node_config n ON n.id=b.workflow_node_id
                 WHERE b.module_instance_id=? AND b.round_no=? AND b.access_ended_at IS NULL
                   AND REPLACE(REPLACE(n.node_id,'ExpertAssignTask','ExpertReviewTask'),
                               'ExpertSummaryTask','ExpertReviewTask')=?
                """, (rs, row) -> rs.getLong(1), module.id(), module.round(), reviewNodeId);
        batchIds.forEach(batchId -> endExpertAccessBatch(batchId, actorId));
    }

    private void endExpertAccessBatch(Long batchId, Long actorId) {
        List<Map<String, Object>> scopes = jdbc.query("""
                SELECT b.id,m.project_id,m.module_code,b.round_no,
                       REPLACE(REPLACE(n.node_id,'ExpertAssignTask','ExpertReviewTask'),
                               'ExpertSummaryTask','ExpertReviewTask') task_node_id
                  FROM expert_review_batch b JOIN module_instance m ON m.id=b.module_instance_id
                  LEFT JOIN workflow_node_config n ON n.id=b.workflow_node_id
                 WHERE b.id=? AND b.access_ended_at IS NULL
                """, (rs, row) -> linked("batchId", rs.getLong("id"), "projectId", rs.getLong("project_id"),
                        "moduleType", rs.getString("module_code"), "roundNo", rs.getInt("round_no"),
                        "taskNodeId", rs.getString("task_node_id")), batchId);
        if (scopes.isEmpty()) return;
        Map<String, Object> scope = scopes.getFirst();
        List<Long> expertUserIds = jdbc.query("""
                SELECT DISTINCT expert_user_id FROM expert_review_assignment
                 WHERE batch_id=? ORDER BY expert_user_id
                """, (rs, row) -> rs.getLong(1), batchId);
        if (expertUserIds.isEmpty()) {
            throw new IllegalStateException("Expert review batch has no assignments: " + batchId);
        }
        // A legacy batch can point at a node-config row removed by a definition reload.  Keep the
        // normal node-scoped revocation, but include immutable assignment identities so Project can
        // safely revoke only this batch's experts when that historical node id is no longer resolvable.
        scope.put("expertUserIds", expertUserIds);
        outbox.enqueue("workflow.expert-access-ended", EventEnvelope.create("EXPERT_ACCESS_ENDED",
                "EXPERT_REVIEW_BATCH", batchId.toString(), null, null, actorId, scope));
        jdbc.update("UPDATE expert_review_batch SET access_ended_at=CURRENT_TIMESTAMP(6) WHERE id=? AND access_ended_at IS NULL",
                batchId);
    }

    private static boolean isExpertReviewNode(String nodeId) {
        return nodeId != null && nodeId.endsWith("ExpertReviewTask");
    }

    private void recoverSuccessor(Long sourceId) {
        ModuleRecord module = module(sourceId, true);
        ModuleSuccessorPolicy.Completion completion = successors.completion(module.type(), module.state());
        if (!module.finished() || !completion.hasSuccessor()
                || findModuleId(module.projectId(), completion.nextModuleType()) != null) return;
        long actorId = module.startedByUserId() == null ? 0L : module.startedByUserId();
        ActorContext system = new ActorContext(actorId, "SYSTEM", null, null,
                List.of("SYSTEM_ADMIN"), List.of(), "successor-recovery:" + sourceId, 0);
        Long nextModuleInstanceId = ensureSuccessor(module, completion, system);
        StateRecordContract record = latestState(sourceId);
        actions.enqueueLifecycle(module.projectId(), module.id(), module.type(), record, system, completion,
                nextModuleInstanceId);
        log.info("Recovered {} module {} after completed module {} for project {}",
                completion.nextModuleType(), nextModuleInstanceId, sourceId, module.projectId());
    }

    private StateRecordContract latestState(Long moduleInstanceId) {
        return jdbc.query("""
                SELECT * FROM state_record WHERE module_instance_id=? ORDER BY seq DESC LIMIT 1
                """, (rs, row) -> new StateRecordContract(rs.getLong("id"), rs.getLong("module_instance_id"),
                        rs.getInt("seq"), rs.getInt("round_no"), rs.getString("event_type"),
                        rs.getString("from_state"), rs.getString("to_state"), rs.getString("from_node_id"),
                        rs.getString("to_node_id"), rs.getString("result"), rs.getString("summary"),
                        rs.getObject("created_at", LocalDateTime.class)), moduleInstanceId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Completed module has no state history: " + moduleInstanceId));
    }

    private StateTransitionContract lockedTransition(Long id, ActorContext actor, TransitionRequest request, int expected,
            MaterialReservationResult reservation, boolean enforcePermission) {
        ModuleRecord module = module(id, true);
        if (module.finished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module instance is already finished");
        }
        if (module.seq() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "State sequence has changed; refresh and retry");
        }
        if (enforcePermission && !operationPolicy.exactCanOperate(actor, id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current task cannot be operated by this user");
        }
        WorkflowBpmnParseResult model = definitions.model(module.definitionId());
        ResolvedTarget resolved = navigator.transition(model, module.nodeId(), request.eventType(),
                transition -> conditionMatches(transition, module, request));
        TransitionConfig initial = resolved.initialTransition();
        NodeConfig completedNode = node(model, module.nodeId());
        validateExpertTransition(module, completedNode, initial);
        NodeConfig target = resolved.node();
        int seq = module.seq() + 1;
        int round = nextRound(module.round(), resolved.transitions());
        boolean finished = "END_EVENT".equals(target.nodeType());
        String result = firstNonBlank(request.result(), initial.result());
        if (request.nodeFormData() != null) {
            jdbc.update("""
                    INSERT INTO node_form(module_instance_id,node_id,round_no,form_json,submitted_by) VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE form_json=VALUES(form_json),submitted_by=VALUES(submitted_by)
                    """, id, module.nodeId(), module.round(), write(request.nodeFormData()), actor.userId());
        }
        jdbc.update("""
                UPDATE workflow_task SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(6)
                 WHERE module_instance_id=? AND status='OPEN'
                """, id);
        StateRecordContract record = insertState(id, seq, round, request.eventType(), module.state(),
                target.stateCode(), module.nodeId(), target.nodeId(), result, request.remark(), actor);
        jdbc.update("""
                UPDATE module_instance SET current_node_id=?,current_state=?,current_seq=?,current_round=?,finished=?,
                  version=version+1 WHERE id=?
                """, target.nodeId(), target.stateCode(), seq, round, finished, id);
        ModuleSuccessorPolicy.Completion completion = finished
                ? successors.completion(module.type(), target.stateCode()) : null;
        Long nextModuleInstanceId = finished ? ensureSuccessor(module, completion, actor) : null;
        actions.execute(module.projectId(), id, module.type(), completedNode, target, record, resolved.transitions(),
                finished, actor, completion, nextModuleInstanceId);
        if (isExpertReviewNode(completedNode.nodeId()) && !isExpertReviewNode(target.nodeId())) {
            endExpertAccess(module, completedNode.nodeId(), actor.userId());
        }
        if (!finished) createTask(id, module.projectId(), target, actor, round);
        if (reservation != null) {
            Map<Long, String> materialTypes = reservation.materialTypeCodes() == null ? Map.of()
                    : reservation.materialTypeCodes();
            for (Long materialVersionId : reservation.materialVersionIds()) {
                jdbc.update("""
                        INSERT INTO workflow_material_reference(module_instance_id,seq,material_version_id,material_type_code)
                        VALUES (?,?,?,?)
                        """, id, seq, materialVersionId,
                        materialTypes.getOrDefault(materialVersionId, "UNSPECIFIED"));
            }
            outbox.enqueue("material.reservation.confirm", EventEnvelope.create(
                    "MATERIAL_RESERVATION_CONFIRM_REQUESTED", "MATERIAL_RESERVATION", reservation.reservationId(),
                    null, null, actor.userId(), Map.of("reservationId", reservation.reservationId(),
                            "moduleInstanceId", id, "seq", seq)));
        }
        stateEvent(module.projectId(), id, module.type(), record, actor.userId(), null);
        return new StateTransitionContract(record, target.nodeId(), target.stateCode(), finished);
    }

    private Long ensureSuccessor(ModuleRecord module, ModuleSuccessorPolicy.Completion completion, ActorContext actor) {
        if (completion == null || !completion.hasSuccessor()) return null;
        Long existing = findModuleId(module.projectId(), completion.nextModuleType());
        if (existing != null) return existing;
        WorkflowDefinitionCatalog.ActiveDefinition definition = definitions.active(completion.nextModuleType());
        return createModule(module.projectId(), completion.nextModuleType(), actor, definition)
                .stateRecord().moduleInstanceId();
    }

    private Long findModuleId(Long projectId, String moduleType) {
        return jdbc.query("""
                SELECT id FROM module_instance WHERE project_id=? AND module_code=? ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong(1), projectId, moduleType).stream().findFirst().orElse(null);
    }

    private void validateExpertTransition(ModuleRecord module, NodeConfig completedNode, TransitionConfig transition) {
        String eventType = transition.eventType();
        if (!isExpertAssignmentEvent(eventType) && !isExpertReviewSubmitEvent(eventType)) return;
        List<ReviewBatch> batches = jdbc.query("""
                SELECT id,module_instance_id,workflow_node_id,round_no,status,created_by
                  FROM expert_review_batch WHERE module_instance_id=? AND round_no=?
                """, (rs, row) -> new ReviewBatch(rs.getLong("id"), rs.getLong("module_instance_id"),
                        nullableLong(rs, "workflow_node_id"), rs.getInt("round_no"), rs.getString("status"),
                        rs.getLong("created_by")), module.id(), module.round()).stream()
                .filter(batch -> matchesReviewNode(batch, completedNode.nodeId())).toList();
        if (isExpertAssignmentEvent(eventType)) {
            boolean assigned = batches.stream().anyMatch(batch -> {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE batch_id=?",
                        Integer.class, batch.id());
                return count != null && count > 0;
            });
            if (!assigned) throw bad("At least one expert must be assigned before submitting this node");
        }
        if (isExpertReviewSubmitEvent(eventType) && !batches.isEmpty()
                && batches.stream().anyMatch(batch -> !"COMPLETED".equals(batch.status()))) {
            throw bad("Not all experts have submitted their reviews");
        }
    }

    private boolean matchesReviewNode(ReviewBatch batch, String nodeId) {
        if (batch.workflowNodeId() == null) return true;
        String configuredNode = jdbc.query("SELECT node_id FROM workflow_node_config WHERE id=?",
                (rs, row) -> rs.getString(1), batch.workflowNodeId()).stream().findFirst().orElse(null);
        return configuredNode != null
                && Objects.equals(toReviewTaskNode(configuredNode), toReviewTaskNode(nodeId));
    }

    private ReviewBatch reviewBatch(Long batchId) {
        return jdbc.query("""
                SELECT id,module_instance_id,workflow_node_id,round_no,status,created_by
                  FROM expert_review_batch WHERE id=? FOR UPDATE
                """, (rs, row) -> new ReviewBatch(rs.getLong("id"), rs.getLong("module_instance_id"),
                        nullableLong(rs, "workflow_node_id"), rs.getInt("round_no"), rs.getString("status"),
                        rs.getLong("created_by")), batchId).stream().findFirst().orElse(null);
    }

    private static boolean isExpertAssignmentEvent(String eventType) {
        return "DEPT_EXPERT_ASSIGNED".equals(eventType) || "SCIENCE_EXPERT_ASSIGNED".equals(eventType)
                || "EXPERT_ACCEPTANCE_ASSIGNED".equals(eventType);
    }

    private static boolean isExpertReviewSubmitEvent(String eventType) {
        return "DEPT_EXPERT_REVIEW_SUBMITTED".equals(eventType)
                || "SCIENCE_EXPERT_REVIEW_SUBMITTED".equals(eventType)
                || "EXPERT_ACCEPTANCE_REVIEW_SUBMITTED".equals(eventType);
    }

    private static String toReviewTaskNode(String nodeId) {
        if (nodeId == null) return null;
        return nodeId.replace("ExpertAssignTask", "ExpertReviewTask")
                .replace("ExpertSummaryTask", "ExpertReviewTask");
    }

    private boolean conditionMatches(TransitionConfig transition, ModuleRecord module, TransitionRequest request) {
        String type = normalize(transition.conditionType());
        if (type == null || "NONE".equals(type) || "ALWAYS".equals(type)) return true;
        if ("CUSTOM".equals(type) || !blank(transition.conditionHandlerKey())) {
            throw bad("Unsupported conditionHandlerKey: " + transition.conditionHandlerKey());
        }
        Object actual = conditionValue(transition.conditionKey(), module, request);
        return switch (type) {
            case "SIMPLE_BOOL" -> asBoolean(actual) == Boolean.parseBoolean(transition.conditionValue());
            case "EQUALS" -> Objects.equals(actual == null ? null : String.valueOf(actual), transition.conditionValue());
            case "NOT_EQUALS" -> !Objects.equals(actual == null ? null : String.valueOf(actual), transition.conditionValue());
            default -> throw bad("Unsupported conditionType: " + transition.conditionType());
        };
    }

    private Object conditionValue(String key, ModuleRecord module, TransitionRequest request) {
        Map<String, Object> form = request.nodeFormData() == null ? Map.of() : request.nodeFormData();
        if (!blank(key) && form.containsKey(key)) return form.get(key);
        if ("isLimitedProject".equals(key)) {
            return jdbc.query("SELECT limited_project FROM project_application WHERE project_id=?",
                    (rs, row) -> rs.getBoolean(1), module.projectId()).stream().findFirst().orElse(false);
        }
        if ("isSchoolLevelAcceptance".equals(key)) {
            if (form.containsKey("isSchoolLevelAcceptance")) return form.get("isSchoolLevelAcceptance");
            return latestJsonValue("acceptance_form", module.id(), key);
        }
        if (key != null && key.toLowerCase(Locale.ROOT).contains("expert")) {
            String result = jdbc.query("""
                    SELECT final_result FROM expert_review_batch WHERE module_instance_id=? AND status='COMPLETED'
                     ORDER BY id DESC LIMIT 1
                    """, (rs, row) -> rs.getString(1), module.id()).stream().findFirst().orElse(null);
            return positive(result);
        }
        if (form.containsKey("approved")) return form.get("approved");
        if (form.containsKey("result")) return positive(String.valueOf(form.get("result")));
        return positive(firstNonBlank(request.result(), ""));
    }

    private Object latestJsonValue(String table, Long moduleInstanceId, String key) {
        String json = jdbc.query("SELECT payload_json FROM " + table
                + " WHERE module_instance_id=? ORDER BY id DESC LIMIT 1", (rs, row) -> rs.getString(1),
                moduleInstanceId).stream().findFirst().orElse(null);
        if (json == null) return false;
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return values.getOrDefault(key, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to evaluate workflow condition " + key, ex);
        }
    }

    private void createTask(Long moduleId, Long projectId, NodeConfig node, ActorContext actor, int round) {
        Long assignee = "PROJECT_LEADER".equals(node.candidateRoleCode())
                ? requireProjectContext(projectId, actor).ownerUserId() : null;
        jdbc.update("""
                INSERT INTO workflow_task(module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no)
                VALUES (?,?,?,?,'OPEN',?)
                """, moduleId, node.nodeId(), assignee, node.candidateRoleCode(), round);
    }

    private com.amatrix.sic.contracts.ProjectAccessResult requireProjectContext(Long projectId, ActorContext actor) {
        var target = new com.amatrix.sic.contracts.ProjectOperationTarget("context", projectId, "READ", null, null, null);
        var response = projectAccess.decideBatch(tokens.authorization(),
                new com.amatrix.sic.contracts.ProjectAccessBatchRequest(actor, List.of(target)));
        if (!response.success() || response.data() == null || response.data().isEmpty()
                || response.data().getFirst().ownerUserId() == null) {
            throw new IllegalStateException("Project owner is unavailable for project " + projectId);
        }
        var context = response.data().getFirst();
        int updated = jdbc.update("""
                UPDATE project_snapshot SET owner_user_id=?,owner_name_snapshot=?,dept_id=?,source_version=?,
                  updated_at=CURRENT_TIMESTAMP(6) WHERE project_id=?
                """, context.ownerUserId(), context.ownerName(), context.deptId(), context.projectVersion(), projectId);
        if (updated == 0) throw new IllegalStateException("Project snapshot is missing for project " + projectId);
        return context;
    }

    private void requireProjectAccess(Long projectId, ActorContext actor, String operation) {
        var decision = projectAccess.decide(tokens.authorization(), projectId, actor.userId(), actor.deptId(), operation,
                actor.roleCodes());
        if (!decision.success() || decision.data() == null || !decision.data().allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
    }

    private StateRecordContract insertState(Long moduleId, int seq, int round, String event, String fromState,
            String toState, String fromNode, String toNode, String result, String summary, ActorContext actor) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO state_record(module_instance_id,seq,round_no,event_type,from_state,to_state,from_node_id,
                      to_node_id,result,summary,actor_user_id,actor_name_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, moduleId);
            statement.setInt(2, seq);
            statement.setInt(3, round);
            statement.setString(4, event);
            statement.setString(5, fromState);
            statement.setString(6, toState);
            statement.setString(7, fromNode);
            statement.setString(8, toNode);
            statement.setString(9, result);
            statement.setString(10, summary);
            statement.setLong(11, actor.userId());
            statement.setString(12, actor.username());
            return statement;
        }, holder);
        return new StateRecordContract(Objects.requireNonNull(holder.getKey()).longValue(), moduleId, seq, round,
                event, fromState, toState, fromNode, toNode, result, summary, now);
    }

    private void stateEvent(Long projectId, Long moduleId, String type, StateRecordContract record, Long actorId,
            String correlation) {
        outbox.enqueue("workflow.module-state-changed", EventEnvelope.create("MODULE_STATE_CHANGED", "MODULE",
                moduleId.toString(), correlation, null, actorId, linked("type", "MODULE_STATE_CHANGED", "projectId",
                        projectId, "moduleInstanceId", moduleId, "moduleType", type, "fromState", record.fromState(),
                        "toState", record.toState(), "seq", record.seq(), "eventType", record.eventType(),
                        "occurredAt", record.createdAt().toString())));
    }

    private ModuleRecord module(Long id, boolean lock) {
        List<ModuleRecord> rows = jdbc.query("SELECT * FROM module_instance WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new ModuleRecord(rs.getLong("id"), rs.getLong("project_id"),
                        nullableLong(rs, "started_by_user_id"), rs.getString("module_code"),
                        rs.getLong("definition_id"), rs.getString("current_node_id"), rs.getString("current_state"),
                        rs.getInt("current_seq"), rs.getInt("current_round"), rs.getBoolean("finished"),
                        rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class)),
                id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module instance not found");
        return rows.getFirst();
    }

    private NodeConfig node(WorkflowBpmnParseResult model, String nodeId) {
        return model.nodes().stream().filter(item -> Objects.equals(item.nodeId(), nodeId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow node not found: " + nodeId));
    }

    private List<String> requiredMaterialTypes(NodeConfig node) {
        return node.materialRequirements().stream()
                .filter(item -> Boolean.TRUE.equals(item.required()))
                .filter(item -> blank(item.requirementTiming()) || "BEFORE_SUBMIT".equals(item.requirementTiming()))
                .map(MaterialRequirementConfig::materialTypeCode).filter(code -> !blank(code)).distinct().toList();
    }

    private Map<String, Object> transitionMap(TransitionConfig transition) {
        return linked("transitionId", transition.transitionId(), "eventType", transition.eventType(), "result",
                transition.result(), "targetRef", transition.targetRef(), "targetStateCode",
                transition.targetStateCode(), "conditionType", transition.conditionType(), "conditionKey",
                transition.conditionKey(), "conditionValue", transition.conditionValue(), "conditionHandlerKey",
                transition.conditionHandlerKey(), "actionKeys", transition.actionKeys());
    }

    private Map<String, Object> requirementMap(MaterialRequirementConfig requirement) {
        return linked("requirementId", null, "materialTypeCode", requirement.materialTypeCode(), "materialTypeName",
                requirement.materialTypeName(), "required", requirement.required(), "minCount", requirement.minCount(),
                "maxCount", requirement.maxCount(), "usageType", requirement.usageType(), "validatorKey",
                requirement.validatorKey(), "description", requirement.description());
    }

    private Map<String, Object> defaultForm(ModuleRecord module, NodeConfig node) {
        return linked("formCode", module.type() + "_FORM", "moduleType", module.type(), "nodeId", node.nodeId(),
                "stateCode", node.stateCode(), "title", node.nodeName(), "dataKind", module.type() + "_DRAFT",
                "writeMode", "SINGLE_INSTANCE", "supportsFiles", true, "materialTypeCodes",
                node.materialRequirements().stream().map(MaterialRequirementConfig::materialTypeCode).toList());
    }

    private Map<String, Object> stateMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        return linked("stateRecordId", rs.getLong("id"), "moduleInstanceId", rs.getLong("module_instance_id"), "seq",
                rs.getInt("seq"), "roundNo", rs.getInt("round_no"), "eventType", rs.getString("event_type"),
                "fromState", rs.getString("from_state"), "toState", rs.getString("to_state"), "fromNodeId",
                rs.getString("from_node_id"), "toNodeId", rs.getString("to_node_id"), "result",
                rs.getString("result"), "summary", rs.getString("summary"), "createdAt",
                rs.getObject("created_at", LocalDateTime.class));
    }

    private static int nextRound(int current, List<TransitionConfig> transitions) {
        return transitions.stream().anyMatch(transition -> containsReturn(transition.eventType())
                || containsReturn(transition.result())) ? current + 1 : current;
    }

    private static boolean containsReturn(String value) {
        if (value == null) return false;
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("RETURN") || normalized.contains("REJECT");
    }

    private static boolean positive(String result) {
        return result != null && POSITIVE_RESULTS.contains(result.trim().toUpperCase(Locale.ROOT));
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        return positive(String.valueOf(value));
    }

    private static boolean unconditional(TransitionConfig transition) {
        String type = normalize(transition.conditionType());
        return type == null || "NONE".equals(type) || "ALWAYS".equals(type);
    }

    private static String normalizeModule(String value) {
        if (value == null) throw bad("Module type is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPLICATION", "CONTRACT", "ACCEPTANCE").contains(normalized)) {
            throw bad("Unsupported module type");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return blank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return blank(first) ? fallback : first;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }

    private record ModuleRecord(Long id, Long projectId, Long startedByUserId, String type, Long definitionId,
            String nodeId, String state, int seq, int round, boolean finished, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    private record ReviewBatch(Long id, Long moduleInstanceId, Long workflowNodeId, int round, String status,
            Long createdBy) {
    }
}
