package com.amatrix.sicprojectis_backend.workflow;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.amatrix.sicprojectis_backend.material.dao.MaterialTypeDao;
import com.amatrix.sicprojectis_backend.material.entity.MaterialType;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDocumentConfigDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeMaterialRequirementDao;
import com.amatrix.sicprojectis_backend.workflow.dto.UploadWorkflowDefinitionRequest;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
public class WorkflowDefinitionService {
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String RM_NS = "http://example.com/research-management";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowNodeDao workflowNodeDao;
    private final WorkflowNodeMaterialRequirementDao workflowNodeMaterialRequirementDao;
    private final WorkflowNodeDocumentConfigDao workflowNodeDocumentConfigDao;
    private final MaterialTypeDao materialTypeDao;
    private final RoleDao roleDao;

    public WorkflowDefinitionService(
            WorkflowDefinitionDao workflowDefinitionDao,
            WorkflowNodeDao workflowNodeDao,
            WorkflowNodeMaterialRequirementDao workflowNodeMaterialRequirementDao,
            WorkflowNodeDocumentConfigDao workflowNodeDocumentConfigDao,
            MaterialTypeDao materialTypeDao,
            RoleDao roleDao) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.workflowNodeDao = workflowNodeDao;
        this.workflowNodeMaterialRequirementDao = workflowNodeMaterialRequirementDao;
        this.workflowNodeDocumentConfigDao = workflowNodeDocumentConfigDao;
        this.materialTypeDao = materialTypeDao;
        this.roleDao = roleDao;
    }

    @Transactional
    public WorkflowDefinitionSummaryResponse upload(UploadWorkflowDefinitionRequest request) {
        String bpmnXml = normalizeRequired(request.bpmnXml(), "BPMN XML is required");
        ParseResult parseResult = parse(bpmnXml);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setProcessKey(parseResult.processConfig.processKey());
        definition.setProcessName(parseResult.processConfig.processName());
        definition.setModuleType(parseResult.processConfig.moduleType());
        definition.setBpmnXml(bpmnXml);
        definition.setStateMachineRulesJson(toJsonTransitions(parseResult.transitions));
        definition.setVersionNo(parseResult.processConfig.versionNo());
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
        ParseResult parseResult = parse(definition.getBpmnXml());
        ValidationResult validation = validateParse(parseResult);
        return new WorkflowDefinitionDetailResponse(
                toSummary(definition),
                toValidationResponse(parseResult, validation),
                toNodeResponses(parseResult.nodes),
                toTransitionResponses(parseResult.transitions));
    }

    public WorkflowBpmnResponse getBpmn(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return new WorkflowBpmnResponse(definition.getWorkflowDefinitionId(), definition.getBpmnXml());
    }

    public List<WorkflowNodeResponse> getNodes(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return toNodeResponses(parse(definition.getBpmnXml()).nodes);
    }

    public List<WorkflowTransitionResponse> getTransitions(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        return toTransitionResponses(parse(definition.getBpmnXml()).transitions);
    }

    public WorkflowValidationResponse validate(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        ParseResult parseResult = parse(definition.getBpmnXml());
        return toValidationResponse(parseResult, validateParse(parseResult));
    }

    @Transactional
    public WorkflowDefinitionDetailResponse publish(Long workflowDefinitionId) {
        WorkflowDefinition definition = requireDefinition(workflowDefinitionId);
        ParseResult parseResult = parse(definition.getBpmnXml());
        ValidationResult validation = validateParse(parseResult);
        if (!validation.errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", validation.errors));
        }

        deactivateOtherActiveDefinitions(definition.getWorkflowDefinitionId(), parseResult.processConfig.moduleType());
        deleteExistingNodeConfiguration(definition.getWorkflowDefinitionId());

        definition.setProcessKey(parseResult.processConfig.processKey());
        definition.setProcessName(parseResult.processConfig.processName());
        definition.setModuleType(parseResult.processConfig.moduleType());
        definition.setVersionNo(parseResult.processConfig.versionNo());
        definition.setStateMachineRulesJson(toJsonTransitions(parseResult.transitions));
        definition.setStatus(STATUS_ACTIVE);
        workflowDefinitionDao.updateById(definition);

        for (ParsedWorkflowNode parsedNode : parseResult.nodes) {
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

            for (ParsedMaterialRequirement requirement : parsedNode.materialRequirements()) {
                MaterialType materialType = ensureMaterialType(requirement.materialTypeCode(),
                        requirement.materialTypeName(), parseResult.processConfig.moduleType(),
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

            for (ParsedDocumentConfig documentConfig : parsedNode.documentConfigs()) {
                Long outputMaterialTypeId = null;
                if (documentConfig.outputMaterialTypeCode() != null) {
                    MaterialType outputMaterialType = ensureMaterialType(documentConfig.outputMaterialTypeCode(),
                            documentConfig.outputMaterialTypeName(), parseResult.processConfig.moduleType(), null, null);
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

        return new WorkflowDefinitionDetailResponse(
                toSummary(definition),
                toValidationResponse(parseResult, validation),
                toNodeResponses(parseResult.nodes),
                toTransitionResponses(parseResult.transitions));
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

    private MaterialType ensureMaterialType(
            String materialTypeCode,
            String materialTypeName,
            String moduleType,
            String allowedFileTypes,
            Integer maxFileSizeMb) {
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

    private WorkflowDefinitionSummaryResponse toSummary(WorkflowDefinition definition) {
        return new WorkflowDefinitionSummaryResponse(
                definition.getWorkflowDefinitionId(),
                definition.getProcessKey(),
                definition.getProcessName(),
                definition.getModuleType(),
                definition.getVersionNo(),
                definition.getStatus());
    }

    private WorkflowValidationResponse toValidationResponse(ParseResult parseResult, ValidationResult validationResult) {
        return new WorkflowValidationResponse(
                validationResult.errors.isEmpty(),
                parseResult.processConfig.processKey(),
                parseResult.processConfig.processName(),
                parseResult.processConfig.moduleType(),
                parseResult.processConfig.versionNo(),
                parseResult.nodes.size(),
                parseResult.transitions.size(),
                parseResult.nodes.stream()
                        .map(ParsedWorkflowNode::candidateRoleCode)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                parseResult.nodes.stream()
                        .map(ParsedWorkflowNode::stateCode)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList(),
                List.copyOf(validationResult.errors),
                List.copyOf(validationResult.warnings));
    }

    private List<WorkflowNodeResponse> toNodeResponses(List<ParsedWorkflowNode> nodes) {
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

    private List<WorkflowTransitionResponse> toTransitionResponses(List<ParsedTransition> transitions) {
        return transitions.stream()
                .map(transition -> new WorkflowTransitionResponse(
                        transition.sourceRef(),
                        transition.targetRef(),
                        transition.sourceStateCode(),
                        transition.targetStateCode(),
                        transition.eventType(),
                        transition.result(),
                        transition.conditionType(),
                        transition.conditionKey(),
                        transition.conditionValue(),
                        transition.actionKeys()))
                .toList();
    }

    private ValidationResult validateParse(ParseResult parseResult) {
        ValidationResult result = new ValidationResult();
        Set<String> stateCodes = new LinkedHashSet<>();
        for (ParsedWorkflowNode node : parseResult.nodes) {
            if (node.stateCode() == null) {
                result.warnings.add("Node " + node.nodeId() + " does not declare stateCode");
            } else if (!stateCodes.add(node.stateCode())) {
                result.errors.add("Duplicate stateCode: " + node.stateCode());
            }
            if (node.candidateRoleCode() != null) {
                Role role = roleDao.selectByCode(node.candidateRoleCode());
                if (role == null) {
                    result.errors.add("Unknown candidateRoleCode: " + node.candidateRoleCode());
                }
            }
            for (ParsedMaterialRequirement requirement : node.materialRequirements()) {
                if (requirement.materialTypeCode() == null) {
                    result.errors.add("Node " + node.nodeId() + " has materialRequirement without materialTypeCode");
                }
                if (requirement.requirementTiming() == null) {
                    result.errors.add("Node " + node.nodeId() + " has materialRequirement without requirementTiming");
                }
                if (requirement.usageType() == null) {
                    result.errors.add("Node " + node.nodeId() + " has materialRequirement without usageType");
                }
            }
            for (ParsedDocumentConfig document : node.documentConfigs()) {
                if (document.documentTypeCode() == null) {
                    result.errors.add("Node " + node.nodeId() + " has documentConfig without documentTypeCode");
                }
                if (document.generateTiming() == null) {
                    result.errors.add("Node " + node.nodeId() + " has documentConfig without generateTiming");
                }
            }
        }
        if (parseResult.processConfig.processKey() == null) {
            result.errors.add("processConfig.processKey is required");
        }
        if (parseResult.processConfig.moduleType() == null) {
            result.errors.add("processConfig.moduleType is required");
        }
        if (parseResult.processConfig.versionNo() == null) {
            result.errors.add("processConfig.versionNo is required");
        }
        if (parseResult.transitions.isEmpty()) {
            result.warnings.add("No rm:transition definitions were found");
        }
        for (ParsedTransition transition : parseResult.transitions) {
            if (transition.eventType() == null) {
                result.errors.add("Transition " + transition.sourceRef() + " -> " + transition.targetRef()
                        + " is missing eventType");
            }
        }
        return result;
    }

    private ParseResult parse(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));
            Element processConfigElement = firstElement(document.getElementsByTagNameNS(RM_NS, "processConfig"));
            if (processConfigElement == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN is missing rm:processConfig");
            }
            Element processElement = firstElement(document.getElementsByTagNameNS(BPMN_NS, "process"));
            if (processElement == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN is missing process element");
            }

            ParsedProcessConfig processConfig = new ParsedProcessConfig(
                    normalizeOptional(attr(processConfigElement, "processKey")),
                    firstNonBlank(normalizeOptional(attr(processConfigElement, "processName")),
                            normalizeOptional(processElement.getAttribute("name"))),
                    normalizeOptional(attr(processConfigElement, "moduleType")),
                    parseInteger(attr(processConfigElement, "versionNo")),
                    firstNonBlank(normalizeOptional(attr(processConfigElement, "status")), STATUS_DRAFT));

            Map<String, String> laneByNodeId = parseLaneMap(processElement);
            List<ParsedWorkflowNode> nodes = new ArrayList<>();
            Map<String, ParsedWorkflowNode> nodeById = new LinkedHashMap<>();

            for (Element candidate : descendantElements(processElement)) {
                Element workflowNodeElement = directWorkflowNode(candidate);
                if (workflowNodeElement == null) {
                    continue;
                }
                ParsedWorkflowNode parsedNode = parseWorkflowNode(candidate, workflowNodeElement, laneByNodeId);
                nodes.add(parsedNode);
                nodeById.put(parsedNode.nodeId(), parsedNode);
            }

            List<ParsedTransition> transitions = new ArrayList<>();
            for (Element sequenceFlowElement : elementsByLocalName(processElement, "sequenceFlow")) {
                Element transitionElement = directTransition(sequenceFlowElement);
                if (transitionElement == null) {
                    continue;
                }
                String sourceRef = normalizeOptional(sequenceFlowElement.getAttribute("sourceRef"));
                String targetRef = normalizeOptional(sequenceFlowElement.getAttribute("targetRef"));
                ParsedWorkflowNode sourceNode = nodeById.get(sourceRef);
                ParsedWorkflowNode targetNode = nodeById.get(targetRef);
                transitions.add(new ParsedTransition(
                        sourceRef,
                        targetRef,
                        firstNonBlank(normalizeOptional(attr(transitionElement, "sourceStateCode")),
                                sourceNode == null ? null : sourceNode.stateCode()),
                        firstNonBlank(normalizeOptional(attr(transitionElement, "targetStateCode")),
                                targetNode == null ? null : targetNode.stateCode()),
                        normalizeOptional(attr(transitionElement, "eventType")),
                        normalizeOptional(attr(transitionElement, "result")),
                        normalizeOptional(attr(transitionElement, "conditionType")),
                        normalizeOptional(attr(transitionElement, "conditionKey")),
                        normalizeOptional(attr(transitionElement, "conditionValue")),
                        splitCsv(attr(transitionElement, "actionKeys"))));
            }

            return new ParseResult(processConfig, nodes, transitions);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse BPMN XML", ex);
        }
    }

    private ParsedWorkflowNode parseWorkflowNode(Element bpmnNodeElement, Element workflowNodeElement, Map<String, String> laneByNodeId) {
        List<ParsedMaterialRequirement> materialRequirements = childElements(workflowNodeElement, "materialRequirement").stream()
                .map(this::parseMaterialRequirement)
                .toList();
        List<ParsedDocumentConfig> documentConfigs = childElements(workflowNodeElement, "documentConfig").stream()
                .map(this::parseDocumentConfig)
                .toList();
        String nodeId = normalizeOptional(bpmnNodeElement.getAttribute("id"));
        return new ParsedWorkflowNode(
                nodeId,
                firstNonBlank(normalizeOptional(attr(workflowNodeElement, "nodeName")),
                        normalizeOptional(bpmnNodeElement.getAttribute("name")),
                        nodeId),
                toNodeType(bpmnNodeElement.getLocalName()),
                normalizeOptional(attr(workflowNodeElement, "stateCode")),
                firstNonBlank(normalizeOptional(attr(workflowNodeElement, "laneName")), laneByNodeId.get(nodeId)),
                normalizeOptional(attr(workflowNodeElement, "responsibleActorCode")),
                normalizeOptional(attr(workflowNodeElement, "responsibleActorName")),
                normalizeOptional(attr(workflowNodeElement, "candidateRoleCode")),
                normalizeOptional(attr(workflowNodeElement, "operationMode")),
                normalizeOptional(attr(workflowNodeElement, "representedActorCode")),
                normalizeOptional(attr(workflowNodeElement, "representedActorName")),
                materialRequirements,
                documentConfigs);
    }

    private ParsedMaterialRequirement parseMaterialRequirement(Element element) {
        return new ParsedMaterialRequirement(
                normalizeOptional(attr(element, "materialTypeCode")),
                normalizeOptional(attr(element, "materialTypeName")),
                normalizeOptional(attr(element, "requirementTiming")),
                parseBoolean(attr(element, "required")),
                parseInteger(attr(element, "minCount")),
                parseInteger(attr(element, "maxCount")),
                normalizeOptional(attr(element, "usageType")),
                normalizeOptional(attr(element, "validatorKey")),
                normalizeOptional(attr(element, "description")),
                normalizeOptional(attr(element, "allowedFileTypes")),
                parseInteger(attr(element, "maxFileSizeMb")));
    }

    private ParsedDocumentConfig parseDocumentConfig(Element element) {
        return new ParsedDocumentConfig(
                normalizeOptional(attr(element, "documentTypeCode")),
                normalizeOptional(attr(element, "documentTypeName")),
                normalizeOptional(attr(element, "generateTiming")),
                normalizeOptional(attr(element, "templateCode")),
                normalizeOptional(attr(element, "snapshotSchemaJson")),
                normalizeOptional(attr(element, "snapshotViewName")),
                normalizeOptional(attr(element, "outputMaterialTypeCode")),
                normalizeOptional(attr(element, "outputMaterialTypeName")),
                parseBoolean(attr(element, "required")),
                parseBooleanDefaultTrue(attr(element, "enabled")));
    }

    private Map<String, String> parseLaneMap(Element processElement) {
        Map<String, String> laneByNodeId = new LinkedHashMap<>();
        for (Element laneElement : elementsByLocalName(processElement, "lane")) {
            String laneName = normalizeOptional(laneElement.getAttribute("name"));
            for (Element flowNodeRefElement : childElements(laneElement, "flowNodeRef")) {
                String flowNodeId = normalizeOptional(flowNodeRefElement.getTextContent());
                if (flowNodeId != null) {
                    laneByNodeId.put(flowNodeId, laneName);
                }
            }
        }
        return laneByNodeId;
    }

    private List<Element> descendantElements(Element root) {
        List<Element> elements = new ArrayList<>();
        collectElements(root, elements);
        return elements;
    }

    private void collectElements(Element current, List<Element> elements) {
        NodeList children = current.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element) {
                elements.add(element);
                collectElements(element, elements);
            }
        }
    }

    private List<Element> elementsByLocalName(Element root, String localName) {
        List<Element> results = new ArrayList<>();
        for (Element element : descendantElements(root)) {
            if (localName.equals(element.getLocalName())) {
                results.add(element);
            }
        }
        return results;
    }

    private Element directWorkflowNode(Element bpmnNodeElement) {
        Element extensionElements = directChild(bpmnNodeElement, BPMN_NS, "extensionElements");
        return extensionElements == null ? null : directChild(extensionElements, RM_NS, "workflowNode");
    }

    private Element directTransition(Element sequenceFlowElement) {
        Element extensionElements = directChild(sequenceFlowElement, BPMN_NS, "extensionElements");
        return extensionElements == null ? null : directChild(extensionElements, RM_NS, "transition");
    }

    private Element directChild(Element parent, String namespace, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element
                    && Objects.equals(element.getNamespaceURI(), namespace)
                    && Objects.equals(element.getLocalName(), localName)) {
                return element;
            }
        }
        return null;
    }

    private List<Element> childElements(Element parent, String localName) {
        List<Element> results = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element
                    && Objects.equals(element.getNamespaceURI(), RM_NS)
                    && Objects.equals(element.getLocalName(), localName)) {
                results.add(element);
            }
        }
        return results;
    }

    private Element firstElement(NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private String attr(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName) : null;
    }

    private Boolean parseBoolean(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : Boolean.parseBoolean(normalized);
    }

    private Boolean parseBooleanDefaultTrue(String value) {
        Boolean parsed = parseBoolean(value);
        return parsed == null ? Boolean.TRUE : parsed;
    }

    private Integer parseInteger(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return Integer.valueOf(normalized);
    }

    private List<String> splitCsv(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String toNodeType(String localName) {
        if (localName == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < localName.length(); i++) {
            char ch = localName.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                result.append('_');
            }
            result.append(Character.toUpperCase(ch));
        }
        return result.toString();
    }

    private String toJsonTransitions(List<ParsedTransition> transitions) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < transitions.size(); i++) {
            ParsedTransition transition = transitions.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append(jsonField("sourceRef", transition.sourceRef())).append(',')
                    .append(jsonField("targetRef", transition.targetRef())).append(',')
                    .append(jsonField("sourceStateCode", transition.sourceStateCode())).append(',')
                    .append(jsonField("targetStateCode", transition.targetStateCode())).append(',')
                    .append(jsonField("eventType", transition.eventType())).append(',')
                    .append(jsonField("result", transition.result())).append(',')
                    .append(jsonField("conditionType", transition.conditionType())).append(',')
                    .append(jsonField("conditionKey", transition.conditionKey())).append(',')
                    .append(jsonField("conditionValue", transition.conditionValue())).append(',')
                    .append("\"actionKeys\":").append(jsonArray(transition.actionKeys()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private String jsonField(String name, String value) {
        return "\"" + escapeJson(name) + "\":" + jsonString(value);
    }

    private String jsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(jsonString(values.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ParseResult(
            ParsedProcessConfig processConfig,
            List<ParsedWorkflowNode> nodes,
            List<ParsedTransition> transitions) {
    }

    private record ParsedProcessConfig(
            String processKey,
            String processName,
            String moduleType,
            Integer versionNo,
            String status) {
    }

    private record ParsedWorkflowNode(
            String nodeId,
            String nodeName,
            String nodeType,
            String stateCode,
            String laneName,
            String responsibleActorCode,
            String responsibleActorName,
            String candidateRoleCode,
            String operationMode,
            String representedActorCode,
            String representedActorName,
            List<ParsedMaterialRequirement> materialRequirements,
            List<ParsedDocumentConfig> documentConfigs) {
    }

    private record ParsedMaterialRequirement(
            String materialTypeCode,
            String materialTypeName,
            String requirementTiming,
            Boolean required,
            Integer minCount,
            Integer maxCount,
            String usageType,
            String validatorKey,
            String description,
            String allowedFileTypes,
            Integer maxFileSizeMb) {
    }

    private record ParsedDocumentConfig(
            String documentTypeCode,
            String documentTypeName,
            String generateTiming,
            String templateCode,
            String snapshotSchemaJson,
            String snapshotViewName,
            String outputMaterialTypeCode,
            String outputMaterialTypeName,
            Boolean required,
            Boolean enabled) {
    }

    private record ParsedTransition(
            String sourceRef,
            String targetRef,
            String sourceStateCode,
            String targetStateCode,
            String eventType,
            String result,
            String conditionType,
            String conditionKey,
            String conditionValue,
            List<String> actionKeys) {
    }

    private static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
    }
}
