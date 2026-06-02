package com.amatrix.sicprojectis_backend.system.entity;

import lombok.Data;

@Data
public class Role {
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String roleDesc;
    private Boolean enabled;
}
