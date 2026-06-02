package com.amatrix.sicprojectis_backend.entity;

import lombok.Data;

@Data
public class UserRoleDetailView {
    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    private Long roleId;
    private String roleCode;
    private String roleName;
}
