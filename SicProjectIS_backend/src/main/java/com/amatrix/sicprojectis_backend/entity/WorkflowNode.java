package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WorkflowNode {
    private Long workflowNodeId;
    private Long workflowDefinitionId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String stateCode;
    private String laneName;
    private String responsibleActorCode;
    private String responsibleActorName;
    private String candidateRoleCode;
    private String operationMode;
    private String representedActorCode;
    private String representedActorName;
    private LocalDateTime createdAt;
}
