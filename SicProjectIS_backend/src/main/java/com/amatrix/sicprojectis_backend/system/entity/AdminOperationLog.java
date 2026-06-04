package com.amatrix.sicprojectis_backend.system.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminOperationLog {
    private Long adminOperationLogId;
    private String scopeType;
    private String actionType;
    private Long operatorUserId;
    private Long targetUserId;
    private Long projectId;
    private String roleCode;
    private String permissionCode;
    private String grantType;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private String remark;
    private LocalDateTime createdAt;
}
