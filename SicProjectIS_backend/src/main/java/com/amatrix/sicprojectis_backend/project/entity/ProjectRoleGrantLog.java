package com.amatrix.sicprojectis_backend.project.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectRoleGrantLog {
    private Long grantLogId;
    private Long projectRoleGrantId;
    private String actionType;
    private Long operatorUserId;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private String remark;
    private LocalDateTime createdAt;
}
