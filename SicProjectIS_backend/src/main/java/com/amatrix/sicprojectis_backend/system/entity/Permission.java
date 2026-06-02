package com.amatrix.sicprojectis_backend.system.entity;

import lombok.Data;

@Data
public class Permission {
    private Long permissionId;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String permissionDesc;
}
