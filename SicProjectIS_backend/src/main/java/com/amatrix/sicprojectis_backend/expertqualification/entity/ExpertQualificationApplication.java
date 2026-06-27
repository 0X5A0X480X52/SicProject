package com.amatrix.sicprojectis_backend.expertqualification.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ExpertQualificationApplication {
    private Long applicationId;
    private Long applicantUserId;
    private Long applicantDeptId;
    private String specialty;
    private String professionalTitle;
    private String applicationReason;
    private String status;
    private Long deptReviewerUserId;
    private String deptReviewOpinion;
    private String deptReviewRemark;
    private LocalDateTime deptReviewedAt;
    private Long scienceReviewerUserId;
    private String scienceReviewOpinion;
    private String scienceReviewRemark;
    private LocalDateTime scienceReviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

