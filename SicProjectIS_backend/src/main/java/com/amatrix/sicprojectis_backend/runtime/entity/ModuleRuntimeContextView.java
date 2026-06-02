package com.amatrix.sicprojectis_backend.runtime.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ModuleRuntimeContextView {
    private Long moduleInstanceId;
    private Long projectId;
    private String moduleType;
    private Long workflowDefinitionId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer currentSeq;
    private Integer currentRoundNo;
    private String currentState;
    private String currentNodeId;
    private String lastEventType;
    private String lastResult;
    private String lastSummary;
    private LocalDateTime lastTransitionTime;
    private Long currentWorkflowNodeId;
    private String currentNodeName;
    private String currentNodeType;
    private String currentLaneName;
    private String currentResponsibleActorCode;
    private String currentResponsibleActorName;
    private String currentCandidateRoleCode;
    private String currentOperationMode;
    private String currentRepresentedActorCode;
    private String currentRepresentedActorName;
}
