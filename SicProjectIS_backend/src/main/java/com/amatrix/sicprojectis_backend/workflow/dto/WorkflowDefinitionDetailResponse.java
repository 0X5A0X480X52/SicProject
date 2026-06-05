package com.amatrix.sicprojectis_backend.workflow.dto;

import java.util.List;

public record WorkflowDefinitionDetailResponse(
        WorkflowDefinitionSummaryResponse definition,
        WorkflowValidationResponse validation,
        List<WorkflowNodeResponse> nodes,
        List<WorkflowTransitionResponse> transitions) {
}
