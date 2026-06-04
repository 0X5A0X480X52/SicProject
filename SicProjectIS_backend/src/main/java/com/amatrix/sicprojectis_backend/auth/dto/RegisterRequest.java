package com.amatrix.sicprojectis_backend.auth.dto;

public record RegisterRequest(
        String username,
        String password,
        String realName,
        Long deptId,
        String phone,
        String email) {
}
