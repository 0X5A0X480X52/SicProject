package com.amatrix.sicprojectis_backend.system.dto;

public record PermissionDefinitionResponse(
        Long permissionId,
        String permissionCode,
        String permissionName,
        String permissionGroup,
        String permissionDesc) {
}
