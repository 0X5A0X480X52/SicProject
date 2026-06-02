package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AppUser {
    private Long userId;
    private String username;
    private String passwordHash;
    private String realName;
    private Long deptId;
    private String phone;
    private String email;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
