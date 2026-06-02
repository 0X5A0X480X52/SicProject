package com.amatrix.sicprojectis_backend.workflow.entity;

import lombok.Data;

@Data
public class WorkflowNodeMaterialRequirement {
    private Long requirementId;
    private Long workflowNodeId;
    private Long materialTypeId;
    private String requirementTiming;
    private Boolean required;
    private Integer minCount;
    private Integer maxCount;
    private String usageType;
    private String validatorKey;
    private String description;
}
