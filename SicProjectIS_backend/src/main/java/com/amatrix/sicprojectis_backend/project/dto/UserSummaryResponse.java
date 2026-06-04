package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

public record UserSummaryResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        List<String> roleCodes) {
}
