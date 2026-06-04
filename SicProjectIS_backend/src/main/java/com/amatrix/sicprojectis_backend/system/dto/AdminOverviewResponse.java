package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AdminOverviewResponse(
        long totalUsers,
        long enabledUsers,
        long totalRoles,
        long totalPermissions,
        long activeProjectGrants,
        long auditLogCount,
        List<AdminCountItemResponse> grantTypeCounts,
        List<AdminCountItemResponse> roleCounts) {
}
