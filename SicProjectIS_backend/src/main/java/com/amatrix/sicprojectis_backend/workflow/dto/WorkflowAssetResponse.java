package com.amatrix.sicprojectis_backend.workflow.dto;

public record WorkflowAssetResponse(
        String assetName,
        String resourcePath,
        String processKey,
        String moduleType,
        Integer versionNo) {
}
