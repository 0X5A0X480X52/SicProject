package com.amatrix.sicprojectis_backend.workflow.dto;

import java.util.List;

public record WorkflowTransitionResponse(
        String sourceRef,
        String targetRef,
        String sourceStateCode,
        String targetStateCode,
        String eventType,
        String result,
        String conditionType,
        String conditionKey,
        String conditionValue,
        List<String> actionKeys) {
}
