package com.amatrix.sicprojectis_backend.structured.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AcceptanceFinancialSettlement {
    private Long settlementId;
    private Long acceptanceId;
    private Long projectId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private BigDecimal approvedAmount;
    private BigDecimal receivedAmount;
    private BigDecimal spentAmount;
    private BigDecimal surplusAmount;
    private BigDecimal executionRate;
    private String settlementResult;
    private Long financeOperatorId;
    private String financeReviewComment;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
