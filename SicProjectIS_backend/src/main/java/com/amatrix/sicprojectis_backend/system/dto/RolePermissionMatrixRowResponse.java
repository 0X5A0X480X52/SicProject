package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record RolePermissionMatrixRowResponse(
        String roleCode,
        String roleName,
        boolean enabled,
        List<String> permissionCodes) {
}
