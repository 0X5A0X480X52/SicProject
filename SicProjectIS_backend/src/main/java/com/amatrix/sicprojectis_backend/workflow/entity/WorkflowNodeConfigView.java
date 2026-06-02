package com.amatrix.sicprojectis_backend.workflow.entity;

import lombok.Data;

@Data
public class WorkflowNodeConfigView {
    private Long workflowNodeId;
    private Long workflowDefinitionId;
    private String processKey;
    private String processName;
    private Integer versionNo;
    private String moduleType;
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
}
