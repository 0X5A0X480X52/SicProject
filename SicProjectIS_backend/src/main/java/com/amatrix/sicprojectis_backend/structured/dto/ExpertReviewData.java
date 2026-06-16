package com.amatrix.sicprojectis_backend.structured.dto;

import java.util.List;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewAssignment;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewScore;

public record ExpertReviewData(ExpertReviewBatch batch, List<ExpertReviewAssignmentData> assignments) {
    public record ExpertReviewAssignmentData(ExpertReviewAssignment assignment, List<ExpertReviewScore> scores) {
    }
}
