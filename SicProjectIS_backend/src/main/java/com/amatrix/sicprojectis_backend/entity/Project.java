package com.amatrix.sicprojectis_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Project {
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long leaderUserId;
    private Long deptId;
    private String projectType;
    private String projectLevel;
    private BigDecimal approvedAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String lifecycleStage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
