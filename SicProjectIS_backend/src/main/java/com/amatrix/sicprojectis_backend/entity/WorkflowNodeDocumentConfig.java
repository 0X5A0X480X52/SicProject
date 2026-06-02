package com.amatrix.sicprojectis_backend.entity;

import lombok.Data;

@Data
public class WorkflowNodeDocumentConfig {
    private Long documentConfigId;
    private Long workflowNodeId;
    private String documentTypeCode;
    private String documentTypeName;
    private String generateTiming;
    private String templateCode;
    private String snapshotSchemaJson;
    private String snapshotViewName;
    private Long outputMaterialTypeId;
    private Boolean required;
    private Boolean enabled;
}
