package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AdminUserDetailResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        String phone,
        String email,
        boolean enabled,
        List<String> roleCodes,
        boolean canEditRoles,
        boolean canToggleStatus) {
}
