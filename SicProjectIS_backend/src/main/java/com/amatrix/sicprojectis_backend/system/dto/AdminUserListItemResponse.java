package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AdminUserListItemResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        boolean enabled,
        List<String> roleCodes,
        boolean canEditRoles,
        boolean canToggleStatus) {
}
