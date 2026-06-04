package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

public record ProjectAuthorizationDetailResponse(
        ProjectSummaryResponse project,
        UserSummaryResponse leader,
        List<ProjectMemberResponse> members,
        List<ProjectGrantResponse> expertGrants,
        List<ProjectGrantResponse> financeGrants,
        List<ProjectGrantResponse> proxyGrants,
        List<UserSummaryResponse> users,
        ProjectAuthorizationCapabilitiesResponse capabilities) {
}
