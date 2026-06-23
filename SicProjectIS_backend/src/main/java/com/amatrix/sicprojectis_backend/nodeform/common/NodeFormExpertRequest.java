package com.amatrix.sicprojectis_backend.nodeform.common;

import com.amatrix.sicprojectis_backend.expert.dto.AssignExpertRequest;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.expert.dto.SubmitExpertScoreRequest;

public record NodeFormExpertRequest(
        Long batchId,
        Long assignmentId,
        CreateExpertReviewBatchRequest createBatch,
        AssignExpertRequest assignExpert,
        SubmitExpertScoreRequest submitScore) {
}
