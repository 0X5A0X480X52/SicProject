package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AdminUserRoleRecordResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        boolean enabled,
        List<String> roleCodes) {
}
