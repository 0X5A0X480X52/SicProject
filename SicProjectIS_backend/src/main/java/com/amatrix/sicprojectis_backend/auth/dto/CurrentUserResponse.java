package com.amatrix.sicprojectis_backend.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        List<String> roleCodes,
        List<String> permissionCodes
) {
}
