package com.amatrix.sicprojectis_backend.workflow.dto;

import java.util.List;

public record WorkflowNodeResponse(
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
        List<WorkflowNodeRequirementResponse> materialRequirements,
        List<WorkflowNodeDocumentResponse> documentConfigs) {
}
