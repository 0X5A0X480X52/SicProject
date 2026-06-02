package com.amatrix.sicprojectis_backend.system.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserRole {
    private Long userRoleId;
    private Long userId;
    private Long roleId;
    private LocalDateTime assignedAt;
}
