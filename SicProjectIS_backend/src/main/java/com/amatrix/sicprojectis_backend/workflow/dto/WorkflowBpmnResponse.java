package com.amatrix.sicprojectis_backend.workflow.dto;

public record WorkflowBpmnResponse(
        Long workflowDefinitionId,
        String bpmnXml) {
}
