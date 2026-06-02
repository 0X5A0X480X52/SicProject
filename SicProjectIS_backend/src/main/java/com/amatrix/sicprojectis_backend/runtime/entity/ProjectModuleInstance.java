package com.amatrix.sicprojectis_backend.runtime.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectModuleInstance {
    private Long moduleInstanceId;
    private Long projectId;
    private String moduleType;
    private Long workflowDefinitionId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
