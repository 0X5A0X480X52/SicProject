package com.amatrix.sicprojectis_backend.system.dto;

import java.time.LocalDateTime;

public record AuditLogRecordResponse(
        String logId,
        String scopeType,
        String actionType,
        Long projectId,
        String projectName,
        AdminUserSummaryResponse operatorUser,
        AdminUserSummaryResponse targetUser,
        String grantType,
        String roleCode,
        String permissionCode,
        String beforeSnapshot,
        String afterSnapshot,
        String remark,
        LocalDateTime createdAt) {
}
