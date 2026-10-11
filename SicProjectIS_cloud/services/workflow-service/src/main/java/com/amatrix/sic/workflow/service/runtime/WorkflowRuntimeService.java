package com.amatrix.sic.workflow.service.runtime;

import com.amatrix.sic.workflow.service.form.NodeFormService;
import com.amatrix.sic.workflow.dto.WorkflowDtos;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionCatalog;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator;
import com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import com.amatrix.sic.workflow.policy.WorkflowProjectAccessPolicy;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.MaterialReservationClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper;
import com.amatrix.sic.workflow.dao.ProjectSnapshotMapper;
import com.amatrix.sic.workflow.entity.ModuleRecord;
import com.amatrix.sic.workflow.entity.ReviewBatch;
import com.amatrix.sic.workflow.entity.LeaderTask;
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
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.MaterialRequirementConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator.ResolvedTarget;
import com.amatrix.sic.workflow.dto.WorkflowDtos.TransitionRequest;

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

    private final WorkflowRuntimeMapper mapper;
    private final ProjectSnapshotMapper snapshots;
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

    public WorkflowRuntimeService(WorkflowRuntimeMapper mapper, ProjectSnapshotMapper snapshots, TransactionTemplate transactions, ProjectAccessClient projectAccess,
            MaterialReservationClient materials, WorkflowInternalTokenProvider tokens,
            WorkflowOperationPolicy operationPolicy, WorkflowDefinitionCatalog definitions,
            WorkflowDefinitionNavigator navigator, WorkflowTransitionActionService actions, NodeFormService nodeForms,
            OutboxService outbox, ObjectMapper objectMapper, WorkflowProjectAccessPolicy workflowAccess,
            ModuleSuccessorPolicy successors) {
        this.mapper = mapper;
        this.snapshots = snapshots;
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
        if (expected != snapshot.seq()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"State sequence has changed; refresh and retry");
        }
        WorkflowBpmnParseResult model = definitions.model(snapshot.definitionId());
        NodeConfig currentNode = node(model, snapshot.nodeId());
        List<String> requiredTypes = requiredMaterialTypes(currentNode);
        MaterialReservationResult reservation = null;
        List<Long> materialIds = request.materialVersionIds() == null ? List.of() : request.materialVersionIds();
        if (!materialIds.isEmpty() || !requiredTypes.isEmpty()) {
            // A failed contender must release only its own lease. expectedSeq already
            // serializes commits; sharing a lease across attempts can revoke the winner.
            String commandId = "transition:" + moduleInstanceId + ":" + expected + ":"
                    + UUID.randomUUID().toString().replace("-", "");
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
        var access = operationPolicy.runtimeAccess(actor, moduleInstanceId, module.projectId());
        boolean canOperate = access.canOperate();
        if (!access.canRead()) {
            // Preserve the existing recovery of an assigned expert's not-yet-synchronized grant.
            if (!workflowAccess.canAccessModule(actor, moduleInstanceId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
            canOperate = operationPolicy.exactCanOperate(actor, moduleInstanceId);
        }
        WorkflowBpmnParseResult model = definitions.model(module.definitionId());
        NodeConfig currentNode = node(model, module.nodeId());
        Long currentWorkflowNodeId = mapper.nodeId(module.definitionId(),module.nodeId());
        List<Map<String, Object>> history = mapper.history(moduleInstanceId);
        List<Map<String, Object>> tasks = mapper.tasks(moduleInstanceId);
        tasks.forEach(task -> task.put("stateCode",module.state()));
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
        Map<String,Object> configuration = Map.of();
        if (!module.finished() && "FINANCE_ADMIN".equals(currentNode.candidateRoleCode())) {
            var response = projectAccess.financeReadiness(tokens.authorization(), module.projectId(), module.type(),
                    module.round(), module.nodeId());
            if (response == null || !response.success() || response.data() == null)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "财务办理配置暂不可用，请稍后重试");
            configuration = response.data();
        }
        return linked("context", context, "canOperate", canOperate, "availableTransitions", transitions,
                "materialRequirements", requirements, "nodeForms", forms,
                "openTasks", tasks, "history", history, "taskConfiguration", configuration);
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> workbench(ActorContext actor) {
        List<Map<String, Object>> candidates = mapper.workbench();
        candidates.forEach(row -> { row.put("todo",true); row.put("canOperate",true); });
        List<Long> ids = candidates.stream().map(row -> (Long) row.get("moduleInstanceId")).toList();
        java.util.Set<Long> allowed = operationPolicy.operableModuleIds(actor, ids);
        return candidates.stream().filter(row -> allowed.contains(row.get("moduleInstanceId"))).toList();
    }

    private StateTransitionContract createModule(Long projectId, String type, ActorContext actor,
            WorkflowDefinitionCatalog.ActiveDefinition definition) {
        ResolvedTarget resolved = navigator.start(definition.model(), WorkflowRuntimeService::unconditional);
        TransitionConfig startTransition = resolved.initialTransition();
        NodeConfig target = resolved.node();
        Map<String,Object> insert=linked("project",projectId,"actor",actor.userId(),"type",type,"definition",definition.id(),
                "node",target.nodeId(),"state",target.stateCode(),"finished","END_EVENT".equals(target.nodeType()));
        mapper.insertModule(insert);
        Long moduleId=((Number)Objects.requireNonNull(insert.get("id"))).longValue();
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
            Long moduleId = mapper.batchModule(batchId);
            if (moduleId == null) return;
            ModuleRecord module = module(moduleId, true);
            ReviewBatch batch = reviewBatch(batchId);
            if (batch == null || !"COMPLETED".equals(batch.status())) return;
            if (!Objects.equals(batch.moduleInstanceId(), module.id())) return;
            if (module.finished() || batch.round() != module.round()) return;
            if (batch.workflowNodeId() != null) {
                String configuredNode = mapper.nodeCode(batch.workflowNodeId());
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
        List<Long> sourceIds = mapper.missingSuccessors();
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
        List<LeaderTask> tasksToCheck = mapper.leaderTasks();
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
            snapshots.owner(context.projectId(),context.ownerUserId(),context.ownerName(),context.deptId(),context.projectVersion(),true);
            mapper.updateLeaderTask(Long.valueOf(context.decisionId()),context.ownerUserId());
        }));
    }

    @Scheduled(initialDelayString = "${sic.workflow.expert-access-recovery-initial-delay-ms:25000}",
            fixedDelayString = "${sic.workflow.expert-access-recovery-delay-ms:300000}")
    public void recoverEndedExpertAccess() {
        List<Long> batchIds = mapper.endedExpertBatches();
        for (Long batchId : batchIds) {
            try {
                transactions.executeWithoutResult(status -> endExpertAccessBatch(batchId, 0L));
            } catch (RuntimeException ex) {
                log.error("Unable to recover ended expert access for review batch {}", batchId, ex);
            }
        }
    }

    private void endExpertAccess(ModuleRecord module, String reviewNodeId, Long actorId) {
        List<Long> batchIds = mapper.nodeExpertBatches(module.id(),module.round(),reviewNodeId);
        batchIds.forEach(batchId -> endExpertAccessBatch(batchId, actorId));
    }

    private void endExpertAccessBatch(Long batchId, Long actorId) {
        Map<String,Object> scope=mapper.endedScope(batchId);
        if(scope==null)return;
        List<Long> expertUserIds=mapper.experts(batchId);
        if (expertUserIds.isEmpty()) {
            throw new IllegalStateException("Expert review batch has no assignments: " + batchId);
        }
        // A legacy batch can point at a node-config row removed by a definition reload.  Keep the
        // normal node-scoped revocation, but include immutable assignment identities so Project can
        // safely revoke only this batch's experts when that historical node id is no longer resolvable.
        scope.put("expertUserIds", expertUserIds);
        outbox.enqueue("workflow.expert-access-ended", EventEnvelope.create("EXPERT_ACCESS_ENDED",
                "EXPERT_REVIEW_BATCH", batchId.toString(), null, null, actorId, scope));
        mapper.endBatch(batchId);
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
        StateRecordContract record=mapper.latestState(moduleInstanceId);
        if(record==null)throw new IllegalStateException("Completed module has no state history: " + moduleInstanceId);
        return record;
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
        if (request.nodeFormData() != null) {
            nodeForms.persistTransition(id, module.projectId(), module.type(), module.nodeId(), module.state(),
                    module.round(), request.formCode(), request.nodeFormData(), actor.userId());
        }
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
        mapper.completeTasks(id);
        StateRecordContract record = insertState(id, seq, round, request.eventType(), module.state(),
                target.stateCode(), module.nodeId(), target.nodeId(), result, request.remark(), actor);
        mapper.advanceModule(id,target.nodeId(),target.stateCode(),seq,round,finished);
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
                mapper.reference(id,seq,materialVersionId,materialTypes.getOrDefault(materialVersionId,"UNSPECIFIED"));
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
        return mapper.findModule(projectId,moduleType);
    }

    private void validateExpertTransition(ModuleRecord module, NodeConfig completedNode, TransitionConfig transition) {
        String eventType = transition.eventType();
        if (!isExpertAssignmentEvent(eventType) && !isExpertReviewSubmitEvent(eventType)) return;
        List<ReviewBatch> batches = mapper.batches(module.id(),module.round()).stream()
                .filter(batch -> matchesReviewNode(batch,completedNode.nodeId())).toList();
        if (isExpertAssignmentEvent(eventType)) {
            boolean assigned = batches.stream().anyMatch(batch -> {
                Integer count = mapper.assignmentCount(batch.id());
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
        String configuredNode = mapper.nodeCode(batch.workflowNodeId());
        return configuredNode != null
                && Objects.equals(toReviewTaskNode(configuredNode), toReviewTaskNode(nodeId));
    }

    private ReviewBatch reviewBatch(Long batchId) {
        return mapper.lockBatch(batchId);
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
            return Boolean.TRUE.equals(mapper.limitedProject(module.projectId()));
        }
        if ("isSchoolLevelAcceptance".equals(key)) {
            if (form.containsKey("isSchoolLevelAcceptance")) return form.get("isSchoolLevelAcceptance");
            return latestJsonValue("acceptance_form", module.id(), key);
        }
        if (key != null && key.toLowerCase(Locale.ROOT).contains("expert")) {
            String result = mapper.finalReviewResult(module.id());
            return positive(result);
        }
        if (form.containsKey("approved")) return form.get("approved");
        if (form.containsKey("result")) return positive(String.valueOf(form.get("result")));
        return positive(firstNonBlank(request.result(), ""));
    }

    private Object latestJsonValue(String table, Long moduleInstanceId, String key) {
        String json = mapper.acceptancePayload(moduleInstanceId);
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
        mapper.insertTask(moduleId,node.nodeId(),assignee,node.candidateRoleCode(),round);
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
        int updated = snapshots.owner(projectId,context.ownerUserId(),context.ownerName(),context.deptId(),context.projectVersion(),false);
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
        LocalDateTime now = LocalDateTime.now();
        Map<String,Object> insert=linked("module",moduleId,"seq",seq,"round",round,"event",event,"fromState",fromState,"toState",toState,
                "fromNode",fromNode,"toNode",toNode,"result",result,"summary",summary,"actor",actor.userId(),"name",actor.username());
        mapper.insertState(insert);
        return new StateRecordContract(((Number)Objects.requireNonNull(insert.get("id"))).longValue(), moduleId, seq, round,
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
        ModuleRecord module=mapper.module(id,lock);
        if(module==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");
        return module;
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

}
