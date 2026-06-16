package com.amatrix.sicprojectis_backend.structured.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectApplicationExt {
    private Long applicationExtId;
    private Long applicationId;
    private Long projectId;
    private Long moduleInstanceId;
    private String applicationCategory;
    private String applicationBatchNo;
    private Long applicationNoticeId;
    private String applicationNoticeNo;
    private Boolean isLimitedProject;
    private String limitGroup;
    private BigDecimal expectedBudget;
    private LocalDate expectedStartDate;
    private LocalDate expectedEndDate;
    private Integer deptRecommendRank;
    private BigDecimal deptRecommendScore;
    private String deptRecommendResult;
    private Integer scienceRecommendRank;
    private BigDecimal scienceRecommendScore;
    private String scienceRecommendResult;
    private String authorityApprovalNo;
    private LocalDate authorityApprovalDate;
    private String authorityResult;
    private BigDecimal authorityApprovedAmount;
    private String finalSubmissionNo;
    private LocalDateTime finalSubmissionAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
