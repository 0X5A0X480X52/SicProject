package com.amatrix.sicprojectis_backend.workflow.dto;

public record WorkflowDefinitionSummaryResponse(
        Long workflowDefinitionId,
        String processKey,
        String processName,
        String moduleType,
        Integer versionNo,
        String status) {
}
