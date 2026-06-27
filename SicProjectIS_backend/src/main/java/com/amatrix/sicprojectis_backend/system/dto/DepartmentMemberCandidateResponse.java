package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record DepartmentMemberCandidateResponse(
        List<DepartmentMemberResponse> users
) {
}
