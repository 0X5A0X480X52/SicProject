package com.amatrix.sicprojectis_backend.project.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectAcceptance {
    private Long acceptanceId;
    private Long projectId;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private String certificateNo;
    private String conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
