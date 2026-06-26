package com.amatrix.sicprojectis_backend.expert.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ExpertReviewBatch {
    private Long batchId;
    private Long moduleInstanceId;
    private Long workflowNodeId;
    private Integer roundNo;
    private Long stateRecordId;
    private String reviewType;
    private String reviewTitle;
    private String ruleType;
    private Integer minExpertCount;
    private BigDecimal passScore;
    private BigDecimal recommendScore;
    private Boolean removeHighestLowest;
    private Integer expectedExpertCount;
    private Integer submittedExpertCount;
    private Integer validExpertCount;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private BigDecimal finalScore;
    private String finalResult;
    private Integer rankNo;
    private String summaryComment;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
