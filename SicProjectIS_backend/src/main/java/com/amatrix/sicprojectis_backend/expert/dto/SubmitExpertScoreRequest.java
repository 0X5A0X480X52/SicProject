package com.amatrix.sicprojectis_backend.expert.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubmitExpertScoreRequest(Boolean conflictOfInterest, Boolean valid, String reviewResult,
        String reviewComment, List<ScoreItem> scores) {
    public record ScoreItem(String itemCode, String itemName, BigDecimal weight, BigDecimal maxScore,
            BigDecimal scoreValue, String comment) { }
}
