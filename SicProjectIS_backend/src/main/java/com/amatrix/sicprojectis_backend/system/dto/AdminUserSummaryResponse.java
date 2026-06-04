package com.amatrix.sicprojectis_backend.system.dto;

public record AdminUserSummaryResponse(
        Long userId,
        String username,
        String realName) {
}
