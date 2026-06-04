package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record RolePermissionMatrixResponse(
        List<AdminRoleOptionResponse> roles,
        List<PermissionDefinitionResponse> permissions,
        List<RolePermissionMatrixRowResponse> matrix) {
}
