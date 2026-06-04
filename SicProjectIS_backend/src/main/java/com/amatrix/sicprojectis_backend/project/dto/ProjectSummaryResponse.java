package com.amatrix.sicprojectis_backend.project.dto;

public record ProjectSummaryResponse(
        Long projectId,
        String projectCode,
        String projectName,
        Long deptId,
        String deptName,
        Long leaderUserId,
        String leaderRealName,
        String projectType,
        String projectLevel,
        String lifecycleStage) {
}
