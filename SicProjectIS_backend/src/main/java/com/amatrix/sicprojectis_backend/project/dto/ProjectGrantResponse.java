package com.amatrix.sicprojectis_backend.project.dto;

import java.time.LocalDateTime;

public record ProjectGrantResponse(
        Long projectRoleGrantId,
        String grantRoleCode,
        String grantScope,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        String status,
        String grantReason,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        UserSummaryResponse grantee,
        UserSummaryResponse grantedBy) {
}
