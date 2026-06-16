package com.amatrix.sicprojectis_backend.structured.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectAcceptanceExt {
    private Long acceptanceExtId;
    private Long acceptanceId;
    private Long projectId;
    private Long moduleInstanceId;
    private Boolean isSchoolLevelAcceptance;
    private String acceptanceType;
    private String acceptanceBatchNo;
    private BigDecimal taskCompletionRate;
    private Integer paperCount;
    private Integer patentCount;
    private Integer softwareCopyrightCount;
    private Integer otherAchievementCount;
    private String scienceReviewResult;
    private String authorityReviewResult;
    private LocalDate authorityReviewDate;
    private String authorityFileNo;
    private BigDecimal expertFinalScore;
    private String expertFinalResult;
    private String certificateNo;
    private LocalDate certificateIssueDate;
    private BigDecimal surplusAmount;
    private Boolean surplusReturnRequired;
    private String surplusReturnStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
