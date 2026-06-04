package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record UserRoleManagementResponse(
        List<AdminRoleOptionResponse> roles,
        List<AdminUserRoleRecordResponse> users) {
}
