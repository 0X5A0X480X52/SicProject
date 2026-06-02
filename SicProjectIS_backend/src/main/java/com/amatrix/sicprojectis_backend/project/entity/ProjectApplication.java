package com.amatrix.sicprojectis_backend.project.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectApplication {
    private Long applicationId;
    private Long projectId;
    private String applicationTitle;
    private Boolean isLimitedProject;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String applicationSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
