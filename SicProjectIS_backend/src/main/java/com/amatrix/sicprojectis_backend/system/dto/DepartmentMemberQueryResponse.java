package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record DepartmentMemberQueryResponse(
        List<DepartmentOptionResponse> departments,
        List<String> assignableRoleCodes,
        List<DepartmentMemberResponse> members,
        boolean canManageDepartments,
        boolean memberManagementDisabled,
        String disabledReason
) {
}