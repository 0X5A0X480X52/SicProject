package com.amatrix.sicprojectis_backend.system.dto;

public record DepartmentOptionResponse(
        Long deptId,
        String deptCode,
        String deptName,
        Long parentDeptId,
        boolean enabled
) {
}
