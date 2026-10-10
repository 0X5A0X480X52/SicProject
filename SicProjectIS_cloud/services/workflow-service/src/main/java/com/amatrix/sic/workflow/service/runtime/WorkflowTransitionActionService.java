package com.amatrix.sic.workflow.service.runtime;

import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.DocumentGenerationCommand;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.DocumentConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig;

@Service
public class WorkflowTransitionActionService {
    static final Set<String> OFFICIAL_ACTION_KEYS = Set.of(
            "CHECK_ACCEPTANCE_PROJECT_TYPE", "CHECK_LIMITED_PROJECT",
            "CREATE_ACCEPTANCE_CERTIFICATE_TASK", "CREATE_ACCEPTANCE_FAIL_FILE_TASK",
            "CREATE_ACCEPTANCE_FINAL_SUBMIT_TASK", "CREATE_ACCEPTANCE_MATERIAL_SUBMIT_TASK",
            "CREATE_ACCEPTANCE_SIGN_SEAL_TASK", "CREATE_APPLICATION_DRAFT_TASK",
            "CREATE_AUTHORITY_ACCEPTANCE_REVIEW_TASK", "CREATE_AUTHORITY_CONTRACT_REVIEW_TASK",
            "CREATE_AUTHORITY_REVIEW_RESULT_TASK", "CREATE_AUTHORITY_SEAL_TASK",
            "CREATE_AUTHORITY_SUBMISSION_TASK", "CREATE_CONTRACT_ARCHIVE_TASK",
            "CREATE_CONTRACT_FILL_TASK", "CREATE_DEPT_ACCEPTANCE_REVIEW_TASK",
            "CREATE_DEPT_CONTRACT_REVIEW_TASK", "CREATE_DEPT_EXPERT_REVIEW_TASK",
            "CREATE_DEPT_EXPERT_SUMMARY_TASK", "CREATE_DEPT_NOTIFY_TASK", "CREATE_DEPT_REVIEW_TASK",
            "CREATE_EXPERT_ACCEPTANCE_REVIEW_TASK", "CREATE_EXPERT_ACCEPTANCE_SUMMARY_TASK",
            "CREATE_FINAL_MATERIAL_SUBMIT_TASK", "CREATE_FINANCIAL_SETTLEMENT_TASK",
            "CREATE_LEADER_SIGN_TASK", "CREATE_PRINT_CONTRACT_TASK", "CREATE_PUBLICITY_TASK",
            "CREATE_SCHOOL_SEAL_TASK", "CREATE_SCIENCE_ACCEPTANCE_REVIEW_TASK",
            "CREATE_SCIENCE_CONTRACT_REVIEW_TASK", "CREATE_SCIENCE_EXPERT_REVIEW_TASK",
            "CREATE_SCIENCE_EXPERT_SUMMARY_TASK", "CREATE_SCIENCE_INITIAL_REVIEW_TASK",
            "CREATE_SIGN_AND_SEAL_TASK", "CREATE_SURPLUS_FUNDS_RETURN_TASK",
            "EVALUATE_AUTHORITY_ACCEPTANCE_REVIEW_RESULT", "EVALUATE_AUTHORITY_CONTRACT_REVIEW_RESULT",
            "EVALUATE_AUTHORITY_REVIEW_RESULT", "EVALUATE_DEPT_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_DEPT_CONTRACT_REVIEW_RESULT", "EVALUATE_DEPT_EXPERT_REVIEW_RESULT",
            "EVALUATE_DEPT_REVIEW_RESULT", "EVALUATE_EXPERT_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_PUBLICITY_RESULT", "EVALUATE_SCIENCE_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_SCIENCE_CONTRACT_REVIEW_RESULT", "EVALUATE_SCIENCE_EXPERT_REVIEW_RESULT",
            "EVALUATE_SCIENCE_INITIAL_REVIEW_RESULT", "GENERATE_ACCEPTANCE_DOCUMENTS",
            "GENERATE_ACCEPTANCE_FAIL_DOCUMENTS", "GENERATE_APPLICATION_DOCUMENTS",
            "GENERATE_CONTRACT_DOCUMENTS", "RETURN_ACCEPTANCE_TO_LEADER", "RETURN_CONTRACT_TO_LEADER",
            "RETURN_TO_APPLICANT", "SYSTEM_AUTO");

    private final OutboxService outbox;

    public WorkflowTransitionActionService(OutboxService outbox) {
        this.outbox = outbox;
    }

    public void execute(Long projectId, Long moduleInstanceId, String moduleType, NodeConfig completedNode,
            NodeConfig targetNode, StateRecordContract record, List<TransitionConfig> transitions, boolean finished,
            ActorContext actor, ModuleSuccessorPolicy.Completion completion, Long nextModuleInstanceId) {
        List<String> actionKeys = transitions.stream().flatMap(transition -> transition.actionKeys().stream()).toList();
        validateActions(actionKeys, targetNode, finished);
        enqueueDocuments(projectId, moduleInstanceId, moduleType, completedNode, record, actor,
                "ON_NODE_COMPLETE", "AFTER_APPROVE");
        if (finished) {
            enqueueDocuments(projectId, moduleInstanceId, moduleType, targetNode, record, actor, "ON_PROCESS_END");
            enqueueLifecycle(projectId, moduleInstanceId, moduleType, record, actor, completion,
                    nextModuleInstanceId);
        }
    }

    public void enqueueLifecycle(Long projectId, Long moduleInstanceId, String moduleType,
            StateRecordContract record, ActorContext actor, ModuleSuccessorPolicy.Completion completion,
            Long nextModuleInstanceId) {
        Map<String, Object> payload = linked("projectId", projectId,
                "moduleInstanceId", moduleInstanceId, "completedModuleInstanceId", moduleInstanceId,
                "moduleType", moduleType, "completedModuleType", moduleType, "lifecycleStage",
                completion.lifecycleStage(), "stateRecordId", record.stateRecordId(), "seq", record.seq());
        if (completion.hasSuccessor()) {
            payload.put("nextModuleType", completion.nextModuleType());
            payload.put("nextModuleInstanceId", nextModuleInstanceId);
        }
        outbox.enqueue("workflow.project-lifecycle-changed", EventEnvelope.create(
                "PROJECT_LIFECYCLE_CHANGED", "PROJECT", projectId.toString(), correlation(moduleInstanceId, record),
                record.stateRecordId().toString(), actor.userId(), payload));
    }

    private void validateActions(List<String> actionKeys, NodeConfig targetNode, boolean finished) {
        for (String actionKey : actionKeys) {
            if (blank(actionKey)) continue;
            if (!OFFICIAL_ACTION_KEYS.contains(actionKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Workflow transition uses unsupported actionKey: " + actionKey);
            }
            if (actionKey.startsWith("CREATE_")
                    && (finished || targetNode == null || blank(targetNode.candidateRoleCode()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        actionKey + " target node has no operable candidate role");
            }
        }
    }

    private void enqueueDocuments(Long projectId, Long moduleInstanceId, String moduleType, NodeConfig node,
            StateRecordContract record, ActorContext actor, String... timings) {
        if (node == null || node.documentConfigs() == null) return;
        for (DocumentConfig config : node.documentConfigs()) {
            if (Boolean.FALSE.equals(config.enabled()) || !matches(config.generateTiming(), timings)) continue;
            if (blank(config.documentTypeCode()) || blank(config.outputMaterialTypeCode())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Generated document configuration is incomplete at node " + node.nodeId());
            }
            String commandId = moduleInstanceId + ":" + record.seq() + ":" + config.documentTypeCode();
            Map<String, Object> snapshot = linked("moduleInstanceId", moduleInstanceId, "projectId", projectId,
                    "moduleType", moduleType, "stateRecordId", record.stateRecordId(), "seq", record.seq(),
                    "stateCode", record.toState(), "nodeId", node.nodeId(), "nodeName", node.nodeName(),
                    "eventType", record.eventType(), "result", record.result(), "templateCode", config.templateCode());
            DocumentGenerationCommand command = new DocumentGenerationCommand(commandId, projectId,
                    moduleInstanceId, record.stateRecordId(), record.seq(), moduleType, node.nodeId(),
                    record.toState(), config.documentTypeCode(), config.documentTypeName(), config.templateCode(),
                    config.snapshotSchemaJson(), config.snapshotViewName(), config.outputMaterialTypeCode(),
                    config.outputMaterialTypeName(), config.required(), actor.userId(), snapshot);
            outbox.enqueue("document.generation.requested", EventEnvelope.create("DOCUMENT_GENERATION_REQUESTED",
                    "GENERATED_DOCUMENT", commandId, correlation(moduleInstanceId, record),
                    record.stateRecordId().toString(), actor.userId(), command));
        }
    }

    private static boolean matches(String actual, String... expected) {
        if (actual == null) return false;
        String normalized = actual.trim().toUpperCase(Locale.ROOT);
        for (String candidate : expected) {
            if (normalized.equals(candidate)) return true;
        }
        return false;
    }

    private static String correlation(Long moduleInstanceId, StateRecordContract record) {
        return moduleInstanceId + ":" + record.seq();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
