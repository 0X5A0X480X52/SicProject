package com.amatrix.sicprojectis_backend.system.dto;

public record AdminRoleOptionResponse(
        Long roleId,
        String roleCode,
        String roleName,
        String roleDesc,
        boolean enabled) {
}
