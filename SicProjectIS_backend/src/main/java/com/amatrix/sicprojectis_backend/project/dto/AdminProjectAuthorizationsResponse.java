package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

import com.amatrix.sicprojectis_backend.system.dto.AdminCountItemResponse;

public record AdminProjectAuthorizationsResponse(
        List<ProjectSummaryResponse> projects,
        List<AdminCountItemResponse> grantTypeCounts) {
}
