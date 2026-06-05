package com.amatrix.sicprojectis_backend.workflow.dto;

import java.util.List;

public record WorkflowValidationResponse(
        boolean valid,
        String processKey,
        String processName,
        String moduleType,
        Integer versionNo,
        int nodeCount,
        int transitionCount,
        List<String> roleCodes,
        List<String> stateCodes,
        List<String> errors,
        List<String> warnings) {
}
