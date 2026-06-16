package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectApplicationDetail {
    private Long applicationDetailId;
    private Long applicationId;
    private Long projectId;
    private String researchBackground;
    private String researchObjective;
    private String researchContent;
    private String innovationPoints;
    private String technicalRoute;
    private String schedulePlan;
    private String budgetDescription;
    private String expectedOutcomes;
    private String feasibilityAnalysis;
    private String riskAnalysis;
    private String applicantCommitment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
