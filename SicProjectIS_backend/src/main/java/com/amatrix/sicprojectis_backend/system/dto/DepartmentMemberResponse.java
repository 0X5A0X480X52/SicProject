package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record DepartmentMemberResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        String phone,
        String email,
        boolean enabled,
        List<String> roleCodes,
        List<String> editableRoleCodes
) {
}
