package com.amatrix.sicprojectis_backend.expert.dto;

import java.util.List;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import com.amatrix.sicprojectis_backend.structured.dto.ExpertReviewData.ExpertReviewAssignmentData;

public record ExpertReviewBatchDetailResponse(ExpertReviewBatch batch, List<ExpertReviewAssignmentData> assignments) {
}
