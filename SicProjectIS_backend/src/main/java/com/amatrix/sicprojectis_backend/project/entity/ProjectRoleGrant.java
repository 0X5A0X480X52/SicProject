package com.amatrix.sicprojectis_backend.project.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectRoleGrant {
    private Long projectRoleGrantId;
    private Long projectId;
    private String moduleType;
    private String grantRoleCode;
    private Long granteeUserId;
    private Long grantedByUserId;
    private String grantScope;
    private Integer roundNo;
    private String taskNodeId;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String grantReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
