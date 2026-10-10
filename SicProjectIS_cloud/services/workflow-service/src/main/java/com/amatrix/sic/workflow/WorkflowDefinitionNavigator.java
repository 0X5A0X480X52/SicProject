package com.amatrix.sic.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WorkflowDefinitionNavigator {
    public ResolvedTarget start(WorkflowBpmnParseResult model, Predicate<WorkflowBpmnParseResult.TransitionConfig> condition) {
        WorkflowBpmnParseResult.NodeConfig start = model.nodes().stream()
                .filter(node -> "START_EVENT".equals(node.nodeType()))
                .findFirst()
                .orElseThrow(() -> invalid("Workflow has no start event"));
        List<WorkflowBpmnParseResult.TransitionConfig> outgoing = outgoing(model, start.nodeId());
        if (outgoing.size() != 1) {
            throw invalid("Workflow start event must have exactly one outgoing transition");
        }
        WorkflowBpmnParseResult.TransitionConfig transition = outgoing.getFirst();
        return resolve(model, transition.targetRef(), transition, condition);
    }

    public ResolvedTarget transition(WorkflowBpmnParseResult model, String currentNodeId, String eventType,
            Predicate<WorkflowBpmnParseResult.TransitionConfig> condition) {
        WorkflowBpmnParseResult.TransitionConfig transition = outgoing(model, currentNodeId).stream()
                .filter(item -> Objects.equals(item.eventType(), eventType))
                .findFirst()
                .orElseThrow(() -> invalid("Event is not allowed from current node"));
        return resolve(model, transition.targetRef(), transition, condition);
    }

    public List<WorkflowBpmnParseResult.TransitionConfig> outgoing(WorkflowBpmnParseResult model, String nodeId) {
        return model.transitions().stream()
                .filter(transition -> Objects.equals(transition.sourceRef(), nodeId))
                .toList();
    }

    private ResolvedTarget resolve(WorkflowBpmnParseResult model, String targetId,
            WorkflowBpmnParseResult.TransitionConfig incoming,
            Predicate<WorkflowBpmnParseResult.TransitionConfig> condition) {
        List<WorkflowBpmnParseResult.TransitionConfig> traversed = new ArrayList<>();
        traversed.add(incoming);
        WorkflowBpmnParseResult.NodeConfig node = node(model, targetId);
        int hops = 0;
        while ("GATEWAY".equals(node.nodeType())) {
            if (++hops > 20) {
                throw new IllegalStateException("Gateway traversal exceeded limit");
            }
            List<WorkflowBpmnParseResult.TransitionConfig> matches = outgoing(model, node.nodeId()).stream()
                    .filter(condition)
                    .toList();
            if (matches.isEmpty()) {
                throw invalid("No gateway condition matched at " + node.nodeId());
            }
            if (matches.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Multiple gateway conditions matched at " + node.nodeId());
            }
            WorkflowBpmnParseResult.TransitionConfig selected = matches.getFirst();
            traversed.add(selected);
            node = node(model, selected.targetRef());
        }
        if (!"END_EVENT".equals(node.nodeType()) && blank(node.stateCode())) {
            throw new IllegalStateException("Final workflow node has no stateCode: " + node.nodeId());
        }
        return new ResolvedTarget(node, List.copyOf(traversed));
    }

    private WorkflowBpmnParseResult.NodeConfig node(WorkflowBpmnParseResult model, String nodeId) {
        return model.nodes().stream()
                .filter(node -> Objects.equals(node.nodeId(), nodeId))
                .findFirst()
                .orElseThrow(() -> invalid("Workflow node not found: " + nodeId));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ResolvedTarget(WorkflowBpmnParseResult.NodeConfig node,
            List<WorkflowBpmnParseResult.TransitionConfig> transitions) {
        public WorkflowBpmnParseResult.TransitionConfig initialTransition() {
            return transitions.getFirst();
        }
    }
}
