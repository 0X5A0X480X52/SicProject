package com.amatrix.sicprojectis_backend.project.dto;

import com.amatrix.sicprojectis_backend.system.dto.ChangeDiffSummaryResponse;

public record ProjectAuthorizationMutationResponse(
        ProjectAuthorizationDetailResponse detail,
        ChangeDiffSummaryResponse diff,
        long affectedCount,
        String summaryMessage) {
}
