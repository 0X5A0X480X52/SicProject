package com.amatrix.sicprojectis_backend.expertqualification.dto;

import java.time.LocalDateTime;

import com.amatrix.sicprojectis_backend.project.dto.UserSummaryResponse;

public record ExpertQualificationApplicationResponse(
        Long applicationId,
        UserSummaryResponse applicant,
        Long applicantDeptId,
        String applicantDeptName,
        String specialty,
        String professionalTitle,
        String applicationReason,
        String status,
        UserSummaryResponse deptReviewer,
        String deptReviewOpinion,
        String deptReviewRemark,
        LocalDateTime deptReviewedAt,
        UserSummaryResponse scienceReviewer,
        String scienceReviewOpinion,
        String scienceReviewRemark,
        LocalDateTime scienceReviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

