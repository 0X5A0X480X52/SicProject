package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AdminUserQueryResponse(
        List<AdminRoleOptionResponse> roles,
        List<AdminUserListItemResponse> users) {
}
