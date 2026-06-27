package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record UpdateDepartmentMemberRolesRequest(
        List<String> roleCodes
) {
}
