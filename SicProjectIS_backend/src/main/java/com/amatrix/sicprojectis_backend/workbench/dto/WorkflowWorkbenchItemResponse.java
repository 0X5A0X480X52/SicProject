package com.amatrix.sicprojectis_backend.workbench.dto;

import java.time.LocalDateTime;

public record WorkflowWorkbenchItemResponse(
        Long moduleInstanceId,
        Long projectId,
        String projectCode,
        String projectName,
        String moduleType,
        String lifecycleStage,
        Long workflowDefinitionId,
        String currentState,
        String currentNodeId,
        String currentNodeName,
        String candidateRoleCode,
        Integer currentSeq,
        Integer currentRoundNo,
        LocalDateTime lastTransitionTime,
        boolean finished,
        boolean todo,
        boolean canOperate) {
}
