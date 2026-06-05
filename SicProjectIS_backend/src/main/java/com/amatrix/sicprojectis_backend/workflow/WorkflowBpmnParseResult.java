package com.amatrix.sicprojectis_backend.workflow;

import java.util.List;

public record WorkflowBpmnParseResult(
        ProcessConfig processConfig,
        List<NodeConfig> nodes,
        List<TransitionConfig> transitions) {
    public record ProcessConfig(
            String processKey,
            String processName,
            String moduleType,
            Integer versionNo,
            String status) {
    }

    public record NodeConfig(
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
            List<MaterialRequirementConfig> materialRequirements,
            List<DocumentConfig> documentConfigs) {
    }

    public record MaterialRequirementConfig(
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

    public record DocumentConfig(
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

    public record TransitionConfig(
            String transitionId,
            String sourceRef,
            String targetRef,
            String sourceStateCode,
            String targetStateCode,
            String eventType,
            String result,
            String conditionType,
            String conditionKey,
            String conditionValue,
            String conditionHandlerKey,
            String conditionExpression,
            List<String> actionKeys) {
    }
}
