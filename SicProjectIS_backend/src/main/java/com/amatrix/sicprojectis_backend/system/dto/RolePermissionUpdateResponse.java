package com.amatrix.sicprojectis_backend.system.dto;

public record RolePermissionUpdateResponse(
        String roleCode,
        ChangeDiffSummaryResponse diff,
        RolePermissionMatrixResponse matrix) {
}
