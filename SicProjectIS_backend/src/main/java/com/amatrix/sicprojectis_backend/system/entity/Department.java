package com.amatrix.sicprojectis_backend.system.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Department {
    private Long deptId;
    private String deptCode;
    private String deptName;
    private Long parentDeptId;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
