package com.amatrix.sicprojectis_backend.workflow.dto;

public record WorkflowNodeRequirementResponse(
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
