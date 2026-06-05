package com.amatrix.sicprojectis_backend.workflow.dto;

public record WorkflowNodeDocumentResponse(
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
