package com.amatrix.sicprojectis_backend.workflow.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WorkflowDefinition {
    private Long workflowDefinitionId;
    private String processKey;
    private String processName;
    private String moduleType;
    private String bpmnXml;
    private String stateMachineRulesJson;
    private Integer versionNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
