package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record UpdateRolePermissionsRequest(List<String> permissionCodes) {
}
