package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

public record BatchRevokeProjectGrantRequest(
        List<Long> grantIds,
        String reason) {
}
