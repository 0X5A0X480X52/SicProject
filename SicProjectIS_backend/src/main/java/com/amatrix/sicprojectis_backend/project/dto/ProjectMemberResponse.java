package com.amatrix.sicprojectis_backend.project.dto;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long projectMemberId,
        String memberRole,
        String responsibility,
        LocalDateTime joinedAt,
        UserSummaryResponse user) {
}
