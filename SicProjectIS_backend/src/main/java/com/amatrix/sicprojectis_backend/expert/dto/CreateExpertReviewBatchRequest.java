package com.amatrix.sicprojectis_backend.expert.dto;

import java.math.BigDecimal;

public record CreateExpertReviewBatchRequest(Long moduleInstanceId, Long workflowNodeId, String reviewType,
        String reviewTitle, String ruleType, Integer minExpertCount, BigDecimal passScore,
        BigDecimal recommendScore, Boolean removeHighestLowest, Integer expectedExpertCount) {
}
