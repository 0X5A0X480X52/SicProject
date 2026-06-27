package com.amatrix.sicprojectis_backend.system.dto;

public record SaveDepartmentRequest(
        String deptCode,
        String deptName,
        Long parentDeptId,
        Boolean enabled
) {
}
