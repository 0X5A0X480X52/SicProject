package com.amatrix.sicprojectis_backend.entity;

import lombok.Data;

@Data
public class RolePermission {
    private Long rolePermissionId;
    private Long roleId;
    private Long permissionId;
}
