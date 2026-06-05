package com.amatrix.sicprojectis_backend.workflow;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Event;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.Lane;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FlowableBpmnDefinitionParser {
    private static final String STATUS_DRAFT = "DRAFT";

    public WorkflowBpmnParseResult parse(String bpmnXml) {
        try {
            XMLInputFactory inputFactory = XMLInputFactory.newFactory();
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = inputFactory.createXMLStreamReader(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(reader);
            Process process = model.getMainProcess();
            if (process == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN is missing process element");
            }

            WorkflowBpmnParseResult.ProcessConfig processConfig = parseProcessConfig(process);
            Map<String, String> laneByNodeId = parseLaneMap(process);
            Map<String, WorkflowBpmnParseResult.NodeConfig> nodeById = new LinkedHashMap<>();
            List<WorkflowBpmnParseResult.NodeConfig> nodes = new ArrayList<>();
            List<WorkflowBpmnParseResult.TransitionConfig> transitions = new ArrayList<>();

            for (FlowElement flowElement : allFlowElements(process)) {
                if (flowElement instanceof SequenceFlow sequenceFlow) {
                    transitions.add(parseTransition(sequenceFlow, nodeById));
                    continue;
                }
                if (!isSupportedNode(flowElement)) {
                    continue;
                }
                ExtensionElement workflowNode = firstExtension(flowElement, "workflowNode");
                if (workflowNode == null) {
                    continue;
                }
                WorkflowBpmnParseResult.NodeConfig node = parseNode(flowElement, workflowNode, laneByNodeId);
                nodes.add(node);
                nodeById.put(node.nodeId(), node);
            }

            // Fill source/target state from parsed nodes after all node configs are known.
            List<WorkflowBpmnParseResult.TransitionConfig> normalizedTransitions = transitions.stream()
                    .map(transition -> normalizeTransitionStates(transition, nodeById))
                    .toList();
            return new WorkflowBpmnParseResult(processConfig, nodes, normalizedTransitions);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse BPMN XML with Flowable", ex);
        }
    }

    private WorkflowBpmnParseResult.ProcessConfig parseProcessConfig(Process process) {
        ExtensionElement processConfig = firstExtension(process, "processConfig");
        if (processConfig == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN is missing rm:processConfig");
        }
        return new WorkflowBpmnParseResult.ProcessConfig(
                attr(processConfig, "processKey"),
                firstNonBlank(attr(processConfig, "processName"), process.getName()),
                attr(processConfig, "moduleType"),
                parseInteger(attr(processConfig, "versionNo")),
                firstNonBlank(attr(processConfig, "status"), STATUS_DRAFT));
    }

    private WorkflowBpmnParseResult.NodeConfig parseNode(
            FlowElement flowElement,
            ExtensionElement workflowNode,
            Map<String, String> laneByNodeId) {
        String nodeId = normalizeOptional(flowElement.getId());
        return new WorkflowBpmnParseResult.NodeConfig(
                nodeId,
                firstNonBlank(attr(workflowNode, "nodeName"), flowElement.getName(), nodeId),
                firstNonBlank(attr(workflowNode, "nodeType"), inferNodeType(flowElement)),
                attr(workflowNode, "stateCode"),
                firstNonBlank(attr(workflowNode, "laneName"), laneByNodeId.get(nodeId)),
                attr(workflowNode, "responsibleActorCode"),
                attr(workflowNode, "responsibleActorName"),
                attr(workflowNode, "candidateRoleCode"),
                attr(workflowNode, "operationMode"),
                attr(workflowNode, "representedActorCode"),
                attr(workflowNode, "representedActorName"),
                extensions(flowElement, "materialRequirement").stream().map(this::parseMaterialRequirement).toList(),
                extensions(flowElement, "documentConfig").stream().map(this::parseDocumentConfig).toList());
    }

    private WorkflowBpmnParseResult.MaterialRequirementConfig parseMaterialRequirement(ExtensionElement element) {
        return new WorkflowBpmnParseResult.MaterialRequirementConfig(
                attr(element, "materialTypeCode"),
                attr(element, "materialTypeName"),
                attr(element, "requirementTiming"),
                parseBoolean(attr(element, "required")),
                parseInteger(attr(element, "minCount")),
                parseInteger(attr(element, "maxCount")),
                attr(element, "usageType"),
                attr(element, "validatorKey"),
                attr(element, "description"),
                attr(element, "allowedFileTypes"),
                parseInteger(attr(element, "maxFileSizeMb")));
    }

    private WorkflowBpmnParseResult.DocumentConfig parseDocumentConfig(ExtensionElement element) {
        return new WorkflowBpmnParseResult.DocumentConfig(
                attr(element, "documentTypeCode"),
                attr(element, "documentTypeName"),
                attr(element, "generateTiming"),
                attr(element, "templateCode"),
                attr(element, "snapshotSchemaJson"),
                attr(element, "snapshotViewName"),
                attr(element, "outputMaterialTypeCode"),
                attr(element, "outputMaterialTypeName"),
                parseBoolean(attr(element, "required")),
                parseBooleanDefaultTrue(attr(element, "enabled")));
    }

    private WorkflowBpmnParseResult.TransitionConfig parseTransition(
            SequenceFlow sequenceFlow,
            Map<String, WorkflowBpmnParseResult.NodeConfig> nodeById) {
        ExtensionElement transition = firstExtension(sequenceFlow, "transition");
        if (transition == null) {
            return new WorkflowBpmnParseResult.TransitionConfig(
                    sequenceFlow.getId(),
                    sequenceFlow.getSourceRef(),
                    sequenceFlow.getTargetRef(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    normalizeOptional(sequenceFlow.getConditionExpression()),
                    List.of());
        }
        return normalizeTransitionStates(new WorkflowBpmnParseResult.TransitionConfig(
                sequenceFlow.getId(),
                firstNonBlank(attr(transition, "sourceRef"), sequenceFlow.getSourceRef()),
                firstNonBlank(attr(transition, "targetRef"), sequenceFlow.getTargetRef()),
                attr(transition, "sourceStateCode"),
                attr(transition, "targetStateCode"),
                attr(transition, "eventType"),
                attr(transition, "result"),
                attr(transition, "conditionType"),
                attr(transition, "conditionKey"),
                attr(transition, "conditionValue"),
                attr(transition, "conditionHandlerKey"),
                normalizeOptional(sequenceFlow.getConditionExpression()),
                splitCsv(attr(transition, "actionKeys"))), nodeById);
    }

    private WorkflowBpmnParseResult.TransitionConfig normalizeTransitionStates(
            WorkflowBpmnParseResult.TransitionConfig transition,
            Map<String, WorkflowBpmnParseResult.NodeConfig> nodeById) {
        WorkflowBpmnParseResult.NodeConfig source = nodeById.get(transition.sourceRef());
        WorkflowBpmnParseResult.NodeConfig target = nodeById.get(transition.targetRef());
        return new WorkflowBpmnParseResult.TransitionConfig(
                transition.transitionId(),
                transition.sourceRef(),
                transition.targetRef(),
                firstNonBlank(transition.sourceStateCode(), source == null ? null : source.stateCode()),
                firstNonBlank(transition.targetStateCode(), target == null ? null : target.stateCode()),
                transition.eventType(),
                transition.result(),
                transition.conditionType(),
                transition.conditionKey(),
                transition.conditionValue(),
                transition.conditionHandlerKey(),
                transition.conditionExpression(),
                transition.actionKeys());
    }

    private Map<String, String> parseLaneMap(Process process) {
        Map<String, String> laneByNodeId = new LinkedHashMap<>();
        for (Lane lane : process.getLanes()) {
            for (String flowNodeRef : lane.getFlowReferences()) {
                laneByNodeId.put(flowNodeRef, lane.getName());
            }
        }
        return laneByNodeId;
    }

    private Collection<FlowElement> allFlowElements(Process process) {
        List<FlowElement> result = new ArrayList<>();
        collectFlowElements(process, result);
        return result;
    }

    private void collectFlowElements(FlowElementsContainer container, List<FlowElement> result) {
        for (FlowElement flowElement : container.getFlowElements()) {
            result.add(flowElement);
            if (flowElement instanceof FlowElementsContainer nested) {
                collectFlowElements(nested, result);
            }
        }
    }

    private boolean isSupportedNode(FlowElement flowElement) {
        return flowElement instanceof StartEvent
                || flowElement instanceof UserTask
                || flowElement instanceof ServiceTask
                || flowElement instanceof ExclusiveGateway
                || flowElement instanceof EndEvent
                || flowElement instanceof Gateway
                || flowElement instanceof Event;
    }

    private String inferNodeType(FlowElement flowElement) {
        if (flowElement instanceof StartEvent) {
            return "START_EVENT";
        }
        if (flowElement instanceof EndEvent) {
            return "END_EVENT";
        }
        if (flowElement instanceof UserTask) {
            return "USER_TASK";
        }
        if (flowElement instanceof ServiceTask) {
            return "SERVICE_TASK";
        }
        if (flowElement instanceof ExclusiveGateway) {
            return "GATEWAY";
        }
        if (flowElement instanceof Gateway) {
            return "GATEWAY";
        }
        return flowElement.getClass().getSimpleName().toUpperCase();
    }

    private ExtensionElement firstExtension(BaseElement element, String name) {
        List<ExtensionElement> matches = extensions(element, name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private List<ExtensionElement> extensions(BaseElement element, String name) {
        Map<String, List<ExtensionElement>> extensionElements = element.getExtensionElements();
        if (extensionElements == null || extensionElements.isEmpty()) {
            return List.of();
        }
        List<ExtensionElement> direct = extensionElements.entrySet().stream()
                .filter(entry -> localNameMatches(entry.getKey(), name))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
        if (direct != null) {
            return direct;
        }
        return extensionElements.values().stream()
                .flatMap(List::stream)
                .filter(extension -> localNameMatches(extension.getName(), name))
                .toList();
    }

    private String attr(ExtensionElement element, String name) {
        if (element == null || element.getAttributes() == null) {
            return null;
        }
        return element.getAttributes().values().stream()
                .flatMap(List::stream)
                .filter(attribute -> localNameMatches(attribute.getName(), name))
                .map(attribute -> normalizeOptional(attribute.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean localNameMatches(String actualName, String expectedLocalName) {
        if (actualName == null) {
            return false;
        }
        return Objects.equals(actualName, expectedLocalName)
                || actualName.endsWith(":" + expectedLocalName)
                || actualName.endsWith("}" + expectedLocalName);
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
        return normalized == null ? null : Integer.valueOf(normalized);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
