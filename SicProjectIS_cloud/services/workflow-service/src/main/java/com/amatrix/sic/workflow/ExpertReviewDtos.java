package com.amatrix.sic.workflow;

import java.math.BigDecimal;
import java.util.List;

public final class ExpertReviewDtos {
    private ExpertReviewDtos() {}

    public record CreateBatchRequest(Long moduleInstanceId, Long workflowNodeId, String reviewType, String reviewTitle,
            String ruleType, Integer minExpertCount, BigDecimal passScore, BigDecimal recommendScore,
            Boolean removeHighestLowest, Integer expectedExpertCount) {}

    public record AssignExpertRequest(Long expertUserId, String expertName, String expertOrg, String expertTitle) {}

    public record SubmitScoreRequest(Boolean conflictOfInterest, Boolean valid, String reviewResult, String reviewComment,
            List<ScoreItem> scores) {}

    public record ScoreItem(String itemCode, String itemName, BigDecimal weight, BigDecimal maxScore,
            BigDecimal scoreValue, String comment) {}
}
