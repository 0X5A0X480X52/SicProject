package com.amatrix.sicprojectis_backend.system.dto;

public record UserRoleUpdateResponse(
        AdminUserDetailResponse user,
        ChangeDiffSummaryResponse diff,
        AdminUserQueryResponse query) {
}
