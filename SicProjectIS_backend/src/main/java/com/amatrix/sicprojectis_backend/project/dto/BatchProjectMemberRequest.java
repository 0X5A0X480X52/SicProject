package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

public record BatchProjectMemberRequest(
        List<Long> userIds,
        String responsibility) {
}
