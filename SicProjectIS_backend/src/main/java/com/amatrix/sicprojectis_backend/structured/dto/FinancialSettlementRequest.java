package com.amatrix.sicprojectis_backend.structured.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialSettlementRequest(Long acceptanceId, Long moduleInstanceId, Long stateRecordId,
        BigDecimal approvedAmount, BigDecimal receivedAmount, BigDecimal spentAmount,
        String settlementResult, String financeReviewComment, LocalDateTime settledAt) {
}
