package com.amatrix.sicprojectis_backend.project.dto;

public record ProjectAuthorizationCapabilitiesResponse(
        boolean canManageLeader,
        boolean canManageMembers,
        boolean canManageExperts,
        boolean canManageFinance,
        boolean canManageProxy) {
}
