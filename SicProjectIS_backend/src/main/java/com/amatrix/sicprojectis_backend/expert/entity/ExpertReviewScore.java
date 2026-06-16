package com.amatrix.sicprojectis_backend.expert.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ExpertReviewScore {
    private Long scoreId;
    private Long assignmentId;
    private String scoreItemCode;
    private String scoreItemName;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private BigDecimal scoreValue;
    private String comment;
    private LocalDateTime createdAt;
}
