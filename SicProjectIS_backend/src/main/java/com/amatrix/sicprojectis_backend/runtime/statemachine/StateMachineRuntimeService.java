package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.material.dao.MaterialTypeDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialVersionDao;
import com.amatrix.sicprojectis_backend.nodeform.NodeFormService;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordMaterialDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordRemarkDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordMaterial;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordRemark;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.RuntimeViewResponse;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StartModuleInstanceRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import com.amatrix.sicprojectis_backend.workflow.FlowableBpmnDefinitionParser;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeMaterialRequirementDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowDefinition;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;

@Service
public class StateMachineRuntimeService {
    private final ProjectDao projectDao;
    private final ProjectModuleInstanceDao moduleDao;
    private final ModuleStateRecordDao stateRecordDao;
    private final StateRecordRemarkDao remarkDao;
    private final StateRecordMaterialDao stateRecordMaterialDao;
    private final TaskInstanceDao taskDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowNodeDao workflowNodeDao;
    private final WorkflowNodeMaterialRequirementDao materialRequirementDao;
    private final MaterialTypeDao materialTypeDao;
    private final MaterialVersionDao materialVersionDao;
    private final ModuleRuntimeContextViewDao runtimeContextViewDao;
    private final FlowableBpmnDefinitionParser parser;
    private final PermissionService permissionService;
    private final NodeFormService nodeFormService;
    private final NodeMaterialValidationService materialValidationService;
    private final StructuredTransitionConditionEvaluator conditionEvaluator;
    private final ProcessDocumentGenerationService documentGenerationService;
    private final StateMachineExtensionRegistry extensionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public StateMachineRuntimeService(ProjectDao projectDao, ProjectModuleInstanceDao moduleDao,
            ModuleStateRecordDao stateRecordDao, StateRecordRemarkDao remarkDao,
            StateRecordMaterialDao stateRecordMaterialDao, TaskInstanceDao taskDao,
            WorkflowDefinitionDao workflowDefinitionDao, WorkflowNodeDao workflowNodeDao,
            WorkflowNodeMaterialRequirementDao materialRequirementDao, MaterialTypeDao materialTypeDao,
            MaterialVersionDao materialVersionDao, ModuleRuntimeContextViewDao runtimeContextViewDao,
            FlowableBpmnDefinitionParser parser, PermissionService permissionService, NodeFormService nodeFormService,
            NodeMaterialValidationService materialValidationService,
            StructuredTransitionConditionEvaluator conditionEvaluator,
            ProcessDocumentGenerationService documentGenerationService,
            StateMachineExtensionRegistry extensionRegistry, ApplicationEventPublisher eventPublisher) {
        this.projectDao = projectDao;
        this.moduleDao = moduleDao;
        this.stateRecordDao = stateRecordDao;
        this.remarkDao = remarkDao;
        this.stateRecordMaterialDao = stateRecordMaterialDao;
        this.taskDao = taskDao;
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.workflowNodeDao = workflowNodeDao;
        this.materialRequirementDao = materialRequirementDao;
        this.materialTypeDao = materialTypeDao;
        this.materialVersionDao = materialVersionDao;
        this.runtimeContextViewDao = runtimeContextViewDao;
        this.parser = parser;
        this.permissionService = permissionService;
        this.nodeFormService = nodeFormService;
        this.materialValidationService = materialValidationService;
        this.conditionEvaluator = conditionEvaluator;
        this.documentGenerationService = documentGenerationService;
        this.extensionRegistry = extensionRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StateTransitionResponse startModule(AuthenticatedUser user, Long projectId, StartModuleInstanceRequest request) {
        String moduleType = required(request == null ? null : request.moduleType(), "Module type is required");
        if ("APPLICATION".equals(moduleType)) {
            throw badRequest("APPLICATION must be started with POST /api/project-applications/start");
        }
        requireProjectAccess(user, projectId);
        requireModuleStarter(user);
        if (moduleDao.selectByProjectIdAndModuleType(projectId, moduleType) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module instance already exists");
        }
        WorkflowDefinition definition = workflowDefinitionDao.selectLatestActiveByModuleType(moduleType);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active workflow definition not found");
        }
        WorkflowBpmnParseResult model = parser.parse(definition.getBpmnXml());
        NodeConfig start = startNode(model);
        TransitionConfig startTransition = model.transitions().stream()
                .filter(transition -> Objects.equals(transition.sourceRef(), start.nodeId()))
                .findFirst()
                .orElseThrow(() -> badRequest("Workflow start event has no outgoing transition"));
        NodeConfig target = resolveTarget(model, null, null, startTransition.targetRef(), null).node();
        LocalDateTime now = LocalDateTime.now();

        ProjectModuleInstance module = new ProjectModuleInstance();
        module.setProjectId(projectId);
        module.setModuleType(moduleType);
        module.setWorkflowDefinitionId(definition.getWorkflowDefinitionId());
        module.setStartedAt(now);
        moduleDao.insert(module);

        ModuleStateRecord record = new ModuleStateRecord();
        record.setModuleInstanceId(module.getModuleInstanceId());
        record.setSeq(1);
        record.setRoundNo(1);
        record.setEventType(required(startTransition.eventType(), "Start transition eventType is required"));
        record.setFromState(start.stateCode());
        record.setToState(target.stateCode());
        record.setFromNodeId(start.nodeId());
        record.setToNodeId(target.nodeId());
        record.setResult(startTransition.result());
        record.setSummary("Module started");
        record.setCreatedAt(now);
        stateRecordDao.insert(record);
        if (!"END_EVENT".equals(target.nodeType())) {
            createTask(module.getModuleInstanceId(), target, 1, now);
        } else {
            module.setFinishedAt(now);
            moduleDao.updateById(module);
        }
        publishStateChanged(module, record);
        return new StateTransitionResponse(record, target.nodeId(), target.stateCode(), "END_EVENT".equals(target.nodeType()));
    }

    @Transactional
    public StateTransitionResponse transition(AuthenticatedUser user, Long moduleInstanceId, StateTransitionRequest request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }
        String eventType = required(request.eventType(), "Event type is required");
        ProjectModuleInstance module = requireModuleForUpdate(moduleInstanceId);
        if (module.getFinishedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module instance is already finished");
        }
        requireProjectAccess(user, module.getProjectId());
        ModuleStateRecord current = stateRecordDao.selectLatestByModuleInstanceId(moduleInstanceId);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module instance has not been started");
        }
        int expectedSeq = request.expectedSeq() == null ? -1 : request.expectedSeq();
        if (expectedSeq != current.getSeq()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module state sequence has changed");
        }
        if (!permissionService.canOperateModuleNode(user.userId(), moduleInstanceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current workflow task cannot be operated by this user");
        }
        WorkflowBpmnParseResult model = parser.parse(requireDefinition(module.getWorkflowDefinitionId()).getBpmnXml());
        TransitionConfig initial = model.transitions().stream()
                .filter(transition -> Objects.equals(transition.sourceRef(), current.getToNodeId()))
                .filter(transition -> Objects.equals(transition.eventType(), eventType))
                .findFirst()
                .orElseThrow(() -> badRequest("Event is not allowed from current node"));
        WorkflowNode currentWorkflowNode = workflowNodeDao.selectByWorkflowDefinitionIdAndNodeId(
                module.getWorkflowDefinitionId(), current.getToNodeId());
        materialValidationService.validateBeforeSubmit(module.getProjectId(), currentWorkflowNode, request.materialVersionIds());

        LocalDateTime now = LocalDateTime.now();
        int roundNo = current.getRoundNo() == null ? 1 : current.getRoundNo();
        ModuleStateRecord record = new ModuleStateRecord();
        record.setModuleInstanceId(moduleInstanceId);
        record.setSeq(current.getSeq() + 1);
        record.setRoundNo(roundNo);
        record.setEventType(eventType);
        record.setFromState(current.getToState());
        record.setFromNodeId(current.getToNodeId());
        record.setToState(current.getToState());
        record.setToNodeId(current.getToNodeId());
        record.setResult(firstNonBlank(request.result(), initial.result()));
        record.setSummary(request.remark());
        record.setCreatedAt(now);
        stateRecordDao.insert(record);

        writeRemark(user, record, request, now);
        if (request.formCode() != null && !request.formCode().isBlank()) {
            nodeFormService.saveForTransition(user, request.formCode(), module.getProjectId(), moduleInstanceId,
                    record.getStateRecordId(), request.nodeFormData());
        }
        linkMaterials(record, request.materialVersionIds(), now);

        ResolvedTarget resolvedTarget = resolveTarget(model, module, record, initial.targetRef(), initial);
        NodeConfig target = resolvedTarget.node();
        record.setRoundNo(nextRoundNo(roundNo, target, initial));
        record.setToNodeId(target.nodeId());
        record.setToState(target.stateCode());
        stateRecordDao.updateById(record);
        boolean finished = "END_EVENT".equals(target.nodeType());
        WorkflowNode targetWorkflowNode = workflowNodeDao.selectByWorkflowDefinitionIdAndNodeId(
                module.getWorkflowDefinitionId(), target.nodeId());
        executeActions(module, record, resolvedTarget.transitions(), currentWorkflowNode, targetWorkflowNode, target, finished);
        taskDao.closeOpenByModuleInstanceId(moduleInstanceId);

        documentGenerationService.generateForNodeComplete(module, currentWorkflowNode, record);
        if (finished) {
            module.setFinishedAt(now);
            moduleDao.updateById(module);
            documentGenerationService.generateForProcessEnd(module, targetWorkflowNode, record);
            createFollowUpModuleIfNeeded(module, now);
        } else {
            createTask(moduleInstanceId, target, record.getRoundNo(), now);
        }
        publishStateChanged(module, record);
        return new StateTransitionResponse(record, target.nodeId(), target.stateCode(), finished);
    }

    public RuntimeViewResponse runtimeView(AuthenticatedUser user, Long moduleInstanceId) {
        var context = runtimeContextViewDao.selectByModuleInstanceId(moduleInstanceId);
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module instance not found");
        }
        requireProjectAccess(user, context.getProjectId());
        WorkflowBpmnParseResult model = parser.parse(requireDefinition(context.getWorkflowDefinitionId()).getBpmnXml());
        String currentNodeId = context.getCurrentNodeId();
        List<RuntimeViewResponse.AvailableTransition> transitions = model.transitions().stream()
                .filter(transition -> Objects.equals(transition.sourceRef(), currentNodeId))
                .map(transition -> new RuntimeViewResponse.AvailableTransition(transition.transitionId(),
                        transition.eventType(), transition.result(), transition.targetRef(), transition.targetStateCode(),
                        transition.conditionType(), transition.conditionKey(), transition.conditionValue(),
                        transition.conditionHandlerKey(), transition.actionKeys()))
                .toList();
        WorkflowNode currentNode = currentNodeId == null ? null
                : workflowNodeDao.selectByWorkflowDefinitionIdAndNodeId(context.getWorkflowDefinitionId(), currentNodeId);
        List<RuntimeViewResponse.MaterialRequirementView> requirements = currentNode == null ? List.of()
                : materialRequirementDao.selectByWorkflowNodeId(currentNode.getWorkflowNodeId()).stream()
                        .map(requirement -> {
                            var materialType = materialTypeDao.selectById(requirement.getMaterialTypeId());
                            return new RuntimeViewResponse.MaterialRequirementView(requirement.getRequirementId(),
                                    materialType == null ? null : materialType.getMaterialTypeCode(),
                                    materialType == null ? null : materialType.getMaterialTypeName(),
                                    requirement.getRequired(), requirement.getMinCount(), requirement.getMaxCount(),
                                    requirement.getUsageType(), requirement.getValidatorKey(), requirement.getDescription());
                        })
                        .toList();
        boolean canOperate = permissionService.canOperateModuleNode(user.userId(), moduleInstanceId);
        List<NodeFormDefinition> nodeForms = nodeFormService.definitions().stream()
                .filter(form -> form.moduleType().name().equals(context.getModuleType()))
                .filter(form -> Objects.equals(form.nodeId(), context.getCurrentNodeId())
                        || Objects.equals(form.stateCode(), context.getCurrentState()))
                .toList();
        return new RuntimeViewResponse(context, canOperate, transitions, requirements, nodeForms,
                taskDao.selectOpenByModuleInstanceId(moduleInstanceId),
                stateRecordDao.selectByModuleInstanceId(moduleInstanceId));
    }

    private ResolvedTarget resolveTarget(WorkflowBpmnParseResult model, ProjectModuleInstance module, ModuleStateRecord record,
            String nodeId, TransitionConfig incoming) {
        NodeConfig node = node(model, nodeId);
        List<TransitionConfig> traversedTransitions = new ArrayList<>();
        if (incoming != null) {
            traversedTransitions.add(incoming);
        }
        int hops = 0;
        while ("GATEWAY".equals(node.nodeType())) {
            if (++hops > 20) {
                throw new IllegalStateException("Gateway traversal exceeded limit");
            }
            NodeConfig gateway = node;
            List<TransitionConfig> matches = model.transitions().stream()
                    .filter(transition -> Objects.equals(transition.sourceRef(), gateway.nodeId()))
                    .filter(transition -> conditionMatches(module, record, transition))
                    .toList();
            if (matches.isEmpty()) {
                throw badRequest("No gateway condition matched at " + gateway.nodeId());
            }
            if (matches.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Multiple gateway conditions matched at " + gateway.nodeId());
            }
            incoming = matches.getFirst();
            traversedTransitions.add(incoming);
            node = node(model, incoming.targetRef());
        }
        if (!"END_EVENT".equals(node.nodeType()) && (node.stateCode() == null || node.stateCode().isBlank())) {
            throw new IllegalStateException("Final workflow node has no stateCode: " + node.nodeId());
        }
        return new ResolvedTarget(node, traversedTransitions);
    }

    private record ResolvedTarget(NodeConfig node, List<TransitionConfig> transitions) {
    }

    private boolean conditionMatches(ProjectModuleInstance module, ModuleStateRecord record, TransitionConfig transition) {
        try {
            return conditionEvaluator.matches(new TransitionContext(module, record, transition));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }


    private void executeActions(ProjectModuleInstance module, ModuleStateRecord record, List<TransitionConfig> transitions,
            WorkflowNode completedWorkflowNode, WorkflowNode targetWorkflowNode, NodeConfig targetNode, boolean finished) {
        for (TransitionConfig transition : transitions) {
            TransitionActionContext context = new TransitionActionContext(module, record, transition, completedWorkflowNode,
                    targetWorkflowNode, targetNode, finished);
            for (String actionKey : transition.actionKeys()) {
                TransitionActionHandler handler = extensionRegistry.actionHandler(actionKey);
                if (handler == null) {
                    throw badRequest("Unknown actionKey: " + actionKey);
                }
                handler.execute(actionKey, context);
            }
        }
    }

    private void writeRemark(AuthenticatedUser user, ModuleStateRecord state, StateTransitionRequest request, LocalDateTime now) {
        StateRecordRemark remark = new StateRecordRemark();
        remark.setStateRecordId(state.getStateRecordId());
        remark.setParticipantUserId(user.userId());
        remark.setParticipantType("USER");
        remark.setActionType(request.eventType());
        remark.setResult(state.getResult());
        remark.setIsOperator(true);
        remark.setRemarkContent(request.remark());
        remark.setIsFinal(true);
        remark.setSortNo(1);
        remark.setCreatedAt(now);
        remarkDao.insert(remark);
    }

    private void linkMaterials(ModuleStateRecord state, List<Long> ids, LocalDateTime now) {
        if (ids == null) {
            return;
        }
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            if (materialVersionDao.selectById(id) == null) {
                throw badRequest("Material version not found: " + id);
            }
            StateRecordMaterial link = new StateRecordMaterial();
            link.setStateRecordId(state.getStateRecordId());
            link.setMaterialVersionId(id);
            link.setMaterialUsage("TRANSITION_INPUT");
            link.setIsRequired(false);
            link.setLinkedAt(now);
            stateRecordMaterialDao.insert(link);
        }
    }

    private void createTask(Long moduleInstanceId, NodeConfig node, Integer roundNo, LocalDateTime now) {
        TaskInstance task = new TaskInstance();
        task.setModuleInstanceId(moduleInstanceId);
        task.setNodeId(node.nodeId());
        task.setStateCode(node.stateCode());
        task.setCandidateRoleCode(node.candidateRoleCode());
        task.setTaskStatus("OPEN");
        task.setRoundNo(roundNo);
        task.setCreatedAt(now);
        taskDao.insert(task);
    }

    private int nextRoundNo(int currentRoundNo, NodeConfig target, TransitionConfig transition) {
        String event = transition.eventType() == null ? "" : transition.eventType();
        String result = transition.result() == null ? "" : transition.result();
        if (event.contains("RETURN") || event.contains("REJECT") || result.contains("RETURN")) {
            return currentRoundNo + 1;
        }
        return currentRoundNo;
    }

    private void requireModuleStarter(AuthenticatedUser user) {
        if (user == null || (!permissionService.hasRole(user.userId(), "SCIENCE_ADMIN")
                && !permissionService.hasRole(user.userId(), "SYSTEM_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only science office administrators can start workflow modules");
        }
    }

    private void createFollowUpModuleIfNeeded(ProjectModuleInstance completedModule, LocalDateTime now) {
        if (!"APPLICATION".equals(completedModule.getModuleType())) {
            return;
        }
        if (moduleDao.selectByProjectIdAndModuleType(completedModule.getProjectId(), "CONTRACT") != null) {
            return;
        }
        WorkflowDefinition definition = workflowDefinitionDao.selectLatestActiveByModuleType("CONTRACT");
        if (definition == null) {
            return;
        }
        WorkflowBpmnParseResult model = parser.parse(definition.getBpmnXml());
        NodeConfig start = startNode(model);
        TransitionConfig startTransition = model.transitions().stream()
                .filter(transition -> Objects.equals(transition.sourceRef(), start.nodeId()))
                .findFirst()
                .orElse(null);
        if (startTransition == null) {
            return;
        }
        NodeConfig target = resolveTarget(model, null, null, startTransition.targetRef(), null).node();
        ProjectModuleInstance module = new ProjectModuleInstance();
        module.setProjectId(completedModule.getProjectId());
        module.setModuleType("CONTRACT");
        module.setWorkflowDefinitionId(definition.getWorkflowDefinitionId());
        module.setStartedAt(now);
        moduleDao.insert(module);

        ModuleStateRecord record = new ModuleStateRecord();
        record.setModuleInstanceId(module.getModuleInstanceId());
        record.setSeq(1);
        record.setRoundNo(1);
        record.setEventType(startTransition.eventType());
        record.setFromState(start.stateCode());
        record.setToState(target.stateCode());
        record.setFromNodeId(start.nodeId());
        record.setToNodeId(target.nodeId());
        record.setResult(startTransition.result());
        record.setSummary("Created after application completion");
        record.setCreatedAt(now);
        stateRecordDao.insert(record);
        if (!"END_EVENT".equals(target.nodeType())) {
            createTask(module.getModuleInstanceId(), target, 1, now);
        }
        publishStateChanged(module, record);
    }

    private void publishStateChanged(ProjectModuleInstance module, ModuleStateRecord record) {
        eventPublisher.publishEvent(new ModuleStateChangedEvent(
                module.getProjectId(),
                module.getModuleInstanceId(),
                module.getModuleType(),
                record.getFromState(),
                record.getToState(),
                record.getSeq(),
                record.getEventType(),
                record.getCreatedAt()));
    }
    private ProjectModuleInstance requireModuleForUpdate(Long moduleInstanceId) {
        ProjectModuleInstance module = moduleDao.selectByIdForUpdate(moduleInstanceId);
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module instance not found");
        }
        return module;
    }

    private WorkflowDefinition requireDefinition(Long workflowDefinitionId) {
        WorkflowDefinition definition = workflowDefinitionDao.selectById(workflowDefinitionId);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
        }
        return definition;
    }

    private void requireProjectAccess(AuthenticatedUser user, Long projectId) {
        if (projectId == null || projectDao.selectById(projectId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (user == null || !permissionService.canAccessProject(user.userId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
    }

    private NodeConfig startNode(WorkflowBpmnParseResult model) {
        return model.nodes().stream()
                .filter(node -> "START_EVENT".equals(node.nodeType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow has no start node"));
    }

    private NodeConfig node(WorkflowBpmnParseResult model, String nodeId) {
        return model.nodes().stream()
                .filter(node -> Objects.equals(node.nodeId(), nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow node not found: " + nodeId));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}


