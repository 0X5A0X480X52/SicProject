package com.amatrix.sicprojectis_backend.workflow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sicprojectis_backend.material.dao.MaterialTypeDao;
import com.amatrix.sicprojectis_backend.material.entity.MaterialType;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDocumentConfigDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeMaterialRequirementDao;
import com.amatrix.sicprojectis_backend.workflow.dto.UploadWorkflowDefinitionRequest;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowAssetResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowBpmnResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowDefinitionDetailResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowDefinitionSummaryResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowNodeDocumentResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowNodeRequirementResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowNodeResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowTransitionResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowValidationResponse;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowDefinition;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeDocumentConfig;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeMaterialRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkflowDefinitionService {
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> SIMPLE_BOOL_VALUES = Set.of("true", "false");

    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowNodeDao workflowNodeDao;
    private final WorkflowNodeMaterialRequirementDao workflowNodeMaterialRequirementDao;
    private final WorkflowNodeDocumentConfigDao workflowNodeDocumentConfigDao;
    private final MaterialTypeDao materialTypeDao;
    private final RoleDao roleDao;
    private final FlowableBpmnDefinitionParser parser;
    private final WorkflowAssetService workflowAssetService;
    private final WorkflowDefinitionStaticRegistry staticRegistry;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionService(
            WorkflowDefinitionDao workflowDefinitionDao,
            WorkflowNodeDao workflowNodeDao,
            WorkflowNodeMaterialRequirementDao workflowNodeMaterialRequirementDao,
            WorkflowNodeDocumentConfigDao workflowNodeDocumentConfigDao,
            MaterialTypeDao materialTypeDao,
            RoleDao roleDao,
            FlowableBpmnDefinitionParser parser,
            WorkflowAssetService workflowAssetService,
            WorkflowDefinitionStaticRegistry staticRegistry,
            ObjectMapper objectMapper) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.workflowNodeDao = workflowNodeDao;
        this.workflowNodeMaterialRequirementDao = workflowNodeMaterialRequirementDao;
        this.workflowNodeDocumentConfigDao = workflowNodeDocumentConfigDao;
        this.materialTypeDao = materialTypeDao;
        this.roleDao = roleDao;
        this.parser = parser;
        this.workflowAssetService = workflowAssetService;
        this.staticRegistry = staticRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowDefinitionSummaryResponse upload(UploadWorkflowDefinitionRequest request) {
        return uploadBpmnXml(normalizeRequired(request.bpmnXml(), "BPMN XML is required"));
    }

    @Transactional
    public WorkflowDefinitionSummaryResponse uploadAsset(String assetName) {
        return uploadBpmnXml(workflowAssetService.readAssetXml(assetName));
    }

    @Transactional
    public WorkflowDefinitionDetailResponse publishAsset(String assetName) {
        WorkflowDefinitionSummaryResponse uploaded = uploadAsset(assetName);
        return publish(uploaded.workflowDefinitionId());
    }

    public List<WorkflowAssetResponse> listAssets() {
        return workflowAssetService.listAssets();
    }

    private WorkflowDefinitionSummaryResponse uploadBpmnXml(String bpmnXml) {
        WorkflowBpmnParseResult parseResult = parser.parse(bpmnXml);
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setProcessKey(parseResult.processConfig().processKey());
        definition.setProcessName(parseResult.processConfig().processName());
        definition.setModuleType(parseResult.processConfig().moduleType());
        definition.setBpmnXml(bpmnXml);
        definition.setStateMachineRulesJson(toRulesJson(parseResult.transitions()));
        definition.setVersionNo(parseResult.processConfig().versionNo());
        definition.setStatus(STATUS_DRAFT);
        workflowDefinitionDao.insert(definition);
        return toSummary(definition);
    }

    public List<WorkflowDefinitionSummaryResponse> listDefinitions() {
        return workflowDefinitionDao.selectAll().stream()
                .sorted(Comparator.comparing(WorkflowDefinition::getWorkflowDefinitionId))
                .map(this::toSummary)
                .toList();
    }

    public WorkflowDefinitionDetailResponse getDefinition(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        WorkflowBpmnParseResult parseResult = parser.parse(definition.getBpmnXml());
        ValidationResult validation = validateParse(parseResult);
        return toDetail(definition, parseResult, validation);
    }

    public WorkflowDefinitionSummaryResponse getLatestActiveDefinition(String moduleType) {
        WorkflowDefinition definition = workflowDefinitionDao.selectLatestActiveByModuleType(
                normalizeRequired(moduleType, "Module type is required"));
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active workflow definition not found");
        }
        return toSummary(definition);
    }

    public WorkflowBpmnResponse getBpmn(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return new WorkflowBpmnResponse(definition.getWorkflowDefinitionId(), definition.getBpmnXml());
    }

    public List<WorkflowNodeResponse> getNodes(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return toNodeResponses(parser.parse(definition.getBpmnXml()).nodes());
    }

    public List<WorkflowTransitionResponse> getTransitions(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return toTransitionResponses(parser.parse(definition.getBpmnXml()).transitions());
    }

    public WorkflowValidationResponse validate(Long workflowDefinitionId) {
        WorkflowBpmnParseResult parseResult = parser.parse(requireDefinition(workflowDefinitionId).getBpmnXml());
        return toValidationResponse(parseResult, validateParse(parseResult));
    }

    @Transactional
    public WorkflowDefinitionDetailResponse publish(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        WorkflowBpmnParseResult parseResult = parser.parse(definition.getBpmnXml());
        ValidationResult validation = validateParse(parseResult);
        if (!validation.errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", validation.errors));
        }

        deactivateOtherActiveDefinitions(definition.getWorkflowDefinitionId(), parseResult.processConfig().moduleType());
        deleteExistingNodeConfiguration(definition.getWorkflowDefinitionId());

        definition.setProcessKey(parseResult.processConfig().processKey());
        definition.setProcessName(parseResult.processConfig().processName());
        definition.setModuleType(parseResult.processConfig().moduleType());
        definition.setVersionNo(parseResult.processConfig().versionNo());
        definition.setStateMachineRulesJson(toRulesJson(parseResult.transitions()));
        definition.setStatus(STATUS_ACTIVE);
        workflowDefinitionDao.updateById(definition);

        for (WorkflowBpmnParseResult.NodeConfig parsedNode : parseResult.nodes()) {
            WorkflowNode workflowNode = new WorkflowNode();
            workflowNode.setWorkflowDefinitionId(definition.getWorkflowDefinitionId());
            workflowNode.setNodeId(parsedNode.nodeId());
            workflowNode.setNodeName(parsedNode.nodeName());
            workflowNode.setNodeType(parsedNode.nodeType());
            workflowNode.setStateCode(parsedNode.stateCode());
            workflowNode.setLaneName(parsedNode.laneName());
            workflowNode.setResponsibleActorCode(parsedNode.responsibleActorCode());
            workflowNode.setResponsibleActorName(parsedNode.responsibleActorName());
            workflowNode.setCandidateRoleCode(parsedNode.candidateRoleCode());
            workflowNode.setOperationMode(parsedNode.operationMode());
            workflowNode.setRepresentedActorCode(parsedNode.representedActorCode());
            workflowNode.setRepresentedActorName(parsedNode.representedActorName());
            workflowNode.setCreatedAt(LocalDateTime.now());
            workflowNodeDao.insert(workflowNode);

            for (WorkflowBpmnParseResult.MaterialRequirementConfig requirement : parsedNode.materialRequirements()) {
                MaterialType materialType = ensureMaterialType(requirement.materialTypeCode(),
                        requirement.materialTypeName(), parseResult.processConfig().moduleType(),
                        requirement.allowedFileTypes(), requirement.maxFileSizeMb());
                WorkflowNodeMaterialRequirement entity = new WorkflowNodeMaterialRequirement();
                entity.setWorkflowNodeId(workflowNode.getWorkflowNodeId());
                entity.setMaterialTypeId(materialType.getMaterialTypeId());
                entity.setRequirementTiming(requirement.requirementTiming());
                entity.setRequired(requirement.required());
                entity.setMinCount(requirement.minCount());
                entity.setMaxCount(requirement.maxCount());
                entity.setUsageType(requirement.usageType());
                entity.setValidatorKey(requirement.validatorKey());
                entity.setDescription(requirement.description());
                workflowNodeMaterialRequirementDao.insert(entity);
            }

            for (WorkflowBpmnParseResult.DocumentConfig documentConfig : parsedNode.documentConfigs()) {
                Long outputMaterialTypeId = null;
                if (documentConfig.outputMaterialTypeCode() != null) {
                    MaterialType outputMaterialType = ensureMaterialType(documentConfig.outputMaterialTypeCode(),
                            documentConfig.outputMaterialTypeName(), parseResult.processConfig().moduleType(), null, null);
                    outputMaterialTypeId = outputMaterialType.getMaterialTypeId();
                }
                WorkflowNodeDocumentConfig entity = new WorkflowNodeDocumentConfig();
                entity.setWorkflowNodeId(workflowNode.getWorkflowNodeId());
                entity.setDocumentTypeCode(documentConfig.documentTypeCode());
                entity.setDocumentTypeName(documentConfig.documentTypeName());
                entity.setGenerateTiming(documentConfig.generateTiming());
                entity.setTemplateCode(documentConfig.templateCode());
                entity.setSnapshotSchemaJson(documentConfig.snapshotSchemaJson());
                entity.setSnapshotViewName(documentConfig.snapshotViewName());
                entity.setOutputMaterialTypeId(outputMaterialTypeId);
                entity.setRequired(documentConfig.required());
                entity.setEnabled(documentConfig.enabled());
                workflowNodeDocumentConfigDao.insert(entity);
            }
        }

        return toDetail(definition, parseResult, validation);
    }

    private ValidationResult validateParse(WorkflowBpmnParseResult parseResult) {
        ValidationResult result = new ValidationResult();
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> stateCodes = new LinkedHashSet<>();
        Set<String> nodeIdSet = parseResult.nodes().stream()
                .map(WorkflowBpmnParseResult.NodeConfig::nodeId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        requireConfig(parseResult.processConfig().processKey(), "processConfig.processKey is required", result);
        requireConfig(parseResult.processConfig().moduleType(), "processConfig.moduleType is required", result);
        if (parseResult.processConfig().versionNo() == null) {
            result.errors.add("processConfig.versionNo is required");
        }

        for (WorkflowBpmnParseResult.NodeConfig node : parseResult.nodes()) {
            if (node.nodeId() == null || !nodeIds.add(node.nodeId())) {
                result.errors.add("Duplicate or missing nodeId: " + node.nodeId());
            }
            if (node.stateCode() != null && !stateCodes.add(node.stateCode())) {
                result.errors.add("Duplicate stateCode: " + node.stateCode());
            }
            if (node.candidateRoleCode() != null) {
                Role role = roleDao.selectByCode(node.candidateRoleCode());
                if (role == null) {
                    result.errors.add("Unknown candidateRoleCode: " + node.candidateRoleCode());
                }
            }
            for (WorkflowBpmnParseResult.MaterialRequirementConfig requirement : node.materialRequirements()) {
                requireConfig(requirement.materialTypeCode(), "Node " + node.nodeId() + " has materialRequirement without materialTypeCode", result);
                requireConfig(requirement.requirementTiming(), "Node " + node.nodeId() + " has materialRequirement without requirementTiming", result);
                requireConfig(requirement.usageType(), "Node " + node.nodeId() + " has materialRequirement without usageType", result);
                if (requirement.validatorKey() != null && !staticRegistry.validatorExists(requirement.validatorKey())) {
                    result.errors.add("Unknown validatorKey: " + requirement.validatorKey());
                }
            }
            for (WorkflowBpmnParseResult.DocumentConfig document : node.documentConfigs()) {
                requireConfig(document.documentTypeCode(), "Node " + node.nodeId() + " has documentConfig without documentTypeCode", result);
                requireConfig(document.generateTiming(), "Node " + node.nodeId() + " has documentConfig without generateTiming", result);
            }
        }

        if (parseResult.transitions().isEmpty()) {
            result.warnings.add("No rm:transition definitions were found");
        }
        for (WorkflowBpmnParseResult.TransitionConfig transition : parseResult.transitions()) {
            requireConfig(transition.eventType(), "Transition " + transition.transitionId() + " is missing eventType", result);
            if (transition.sourceRef() == null || !nodeIdSet.contains(transition.sourceRef())) {
                result.errors.add("Transition " + transition.transitionId() + " has unknown sourceRef: " + transition.sourceRef());
            }
            if (transition.targetRef() == null || !nodeIdSet.contains(transition.targetRef())) {
                result.errors.add("Transition " + transition.transitionId() + " has unknown targetRef: " + transition.targetRef());
            }
            if ("SIMPLE_BOOL".equals(transition.conditionType())) {
                requireConfig(transition.conditionKey(), "Transition " + transition.transitionId() + " SIMPLE_BOOL is missing conditionKey", result);
                if (!SIMPLE_BOOL_VALUES.contains(String.valueOf(transition.conditionValue()).toLowerCase())) {
                    result.errors.add("Transition " + transition.transitionId() + " SIMPLE_BOOL has invalid conditionValue: " + transition.conditionValue());
                }
                if (transition.conditionExpression() != null
                        && transition.conditionKey() != null
                        && !transition.conditionExpression().contains(transition.conditionKey())) {
                    result.errors.add("Transition " + transition.transitionId() + " conditionExpression does not reference conditionKey: " + transition.conditionKey());
                }
            }
            if (transition.conditionHandlerKey() != null && !staticRegistry.conditionHandlerExists(transition.conditionHandlerKey())) {
                result.errors.add("Unknown conditionHandlerKey: " + transition.conditionHandlerKey());
            }
            for (String actionKey : transition.actionKeys()) {
                if (!staticRegistry.actionKeyExists(actionKey)) {
                    result.errors.add("Unknown actionKey: " + actionKey);
                }
            }
        }
        return result;
    }

    private void requireConfig(String value, String message, ValidationResult result) {
        if (value == null || value.isBlank()) {
            result.errors.add(message);
        }
    }

    private void deactivateOtherActiveDefinitions(Long currentDefinitionId, String moduleType) {
        for (WorkflowDefinition existing : workflowDefinitionDao.selectAll()) {
            if (!Objects.equals(existing.getWorkflowDefinitionId(), currentDefinitionId)
                    && Objects.equals(existing.getModuleType(), moduleType)
                    && STATUS_ACTIVE.equals(existing.getStatus())) {
                existing.setStatus(STATUS_INACTIVE);
                workflowDefinitionDao.updateById(existing);
            }
        }
    }

    private void deleteExistingNodeConfiguration(Long workflowDefinitionId) {
        for (WorkflowNode node : workflowNodeDao.selectByWorkflowDefinitionId(workflowDefinitionId)) {
            workflowNodeMaterialRequirementDao.deleteByWorkflowNodeId(node.getWorkflowNodeId());
            workflowNodeDocumentConfigDao.deleteByWorkflowNodeId(node.getWorkflowNodeId());
        }
        workflowNodeDao.deleteByWorkflowDefinitionId(workflowDefinitionId);
    }

    private MaterialType ensureMaterialType(String materialTypeCode, String materialTypeName, String moduleType,
            String allowedFileTypes, Integer maxFileSizeMb) {
        MaterialType existing = materialTypeDao.selectByCode(materialTypeCode);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                materialTypeDao.updateById(existing);
            }
            return existing;
        }
        MaterialType materialType = new MaterialType();
        materialType.setMaterialTypeCode(materialTypeCode);
        materialType.setMaterialTypeName(materialTypeName == null ? materialTypeCode : materialTypeName);
        materialType.setModuleType(moduleType);
        materialType.setAllowedFileTypes(allowedFileTypes);
        materialType.setMaxFileSizeMb(maxFileSizeMb);
        materialType.setEnabled(true);
        materialType.setCreatedAt(LocalDateTime.now());
        materialType.setUpdatedAt(LocalDateTime.now());
        materialTypeDao.insert(materialType);
        return materialType;
    }

    private WorkflowDefinition requireDefinition(Long workflowDefinitionId) {
        WorkflowDefinition definition = workflowDefinitionDao.selectById(workflowDefinitionId);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
        }
        return definition;
    }

    private WorkflowDefinitionDetailResponse toDetail(WorkflowDefinition definition, WorkflowBpmnParseResult parseResult,
            ValidationResult validation) {
        return new WorkflowDefinitionDetailResponse(
                toSummary(definition),
                toValidationResponse(parseResult, validation),
                toNodeResponses(parseResult.nodes()),
                toTransitionResponses(parseResult.transitions()));
    }

    private WorkflowDefinitionSummaryResponse toSummary(WorkflowDefinition definition) {
        return new WorkflowDefinitionSummaryResponse(
                definition.getWorkflowDefinitionId(),
                definition.getProcessKey(),
                definition.getProcessName(),
                definition.getModuleType(),
                definition.getVersionNo(),
                definition.getStatus());
    }

    private WorkflowValidationResponse toValidationResponse(WorkflowBpmnParseResult parseResult, ValidationResult validationResult) {
        return new WorkflowValidationResponse(
                validationResult.errors.isEmpty(),
                parseResult.processConfig().processKey(),
                parseResult.processConfig().processName(),
                parseResult.processConfig().moduleType(),
                parseResult.processConfig().versionNo(),
                parseResult.nodes().size(),
                parseResult.transitions().size(),
                parseResult.nodes().stream()
                        .map(WorkflowBpmnParseResult.NodeConfig::candidateRoleCode)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                parseResult.nodes().stream()
                        .map(WorkflowBpmnParseResult.NodeConfig::stateCode)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                List.copyOf(validationResult.errors),
                List.copyOf(validationResult.warnings));
    }

    private List<WorkflowNodeResponse> toNodeResponses(List<WorkflowBpmnParseResult.NodeConfig> nodes) {
        return nodes.stream()
                .map(node -> new WorkflowNodeResponse(
                        node.nodeId(),
                        node.nodeName(),
                        node.nodeType(),
                        node.stateCode(),
                        node.laneName(),
                        node.responsibleActorCode(),
                        node.responsibleActorName(),
                        node.candidateRoleCode(),
                        node.operationMode(),
                        node.representedActorCode(),
                        node.representedActorName(),
                        node.materialRequirements().stream()
                                .map(requirement -> new WorkflowNodeRequirementResponse(
                                        requirement.materialTypeCode(),
                                        requirement.materialTypeName(),
                                        requirement.requirementTiming(),
                                        requirement.required(),
                                        requirement.minCount(),
                                        requirement.maxCount(),
                                        requirement.usageType(),
                                        requirement.validatorKey(),
                                        requirement.description(),
                                        requirement.allowedFileTypes(),
                                        requirement.maxFileSizeMb()))
                                .toList(),
                        node.documentConfigs().stream()
                                .map(document -> new WorkflowNodeDocumentResponse(
                                        document.documentTypeCode(),
                                        document.documentTypeName(),
                                        document.generateTiming(),
                                        document.templateCode(),
                                        document.snapshotSchemaJson(),
                                        document.snapshotViewName(),
                                        document.outputMaterialTypeCode(),
                                        document.outputMaterialTypeName(),
                                        document.required(),
                                        document.enabled()))
                                .toList()))
                .toList();
    }

    private List<WorkflowTransitionResponse> toTransitionResponses(List<WorkflowBpmnParseResult.TransitionConfig> transitions) {
        return transitions.stream()
                .map(transition -> new WorkflowTransitionResponse(
                        transition.transitionId(),
                        transition.sourceRef(),
                        transition.targetRef(),
                        transition.sourceStateCode(),
                        transition.targetStateCode(),
                        transition.eventType(),
                        transition.result(),
                        transition.conditionType(),
                        transition.conditionKey(),
                        transition.conditionValue(),
                        transition.conditionHandlerKey(),
                        transition.conditionExpression(),
                        transition.actionKeys()))
                .toList();
    }

    private String toRulesJson(List<WorkflowBpmnParseResult.TransitionConfig> transitions) {
        try {
            return objectMapper.writeValueAsString(transitions);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize workflow rules", ex);
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
    }
}
