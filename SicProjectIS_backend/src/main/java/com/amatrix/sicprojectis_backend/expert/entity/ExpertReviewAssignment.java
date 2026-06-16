package com.amatrix.sicprojectis_backend.expert.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ExpertReviewAssignment {
    private Long assignmentId;
    private Long batchId;
    private Long expertUserId;
    private String expertName;
    private String expertOrg;
    private String expertTitle;
    private LocalDateTime assignedAt;
    private LocalDateTime submittedAt;
    private String reviewStatus;
    private Boolean conflictOfInterest;
    private Boolean isValid;
    private BigDecimal totalScore;
    private String reviewResult;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
