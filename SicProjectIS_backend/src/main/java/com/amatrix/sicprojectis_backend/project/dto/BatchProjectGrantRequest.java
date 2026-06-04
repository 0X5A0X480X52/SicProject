package com.amatrix.sicprojectis_backend.project.dto;

import java.util.List;

public record BatchProjectGrantRequest(
        List<Long> userIds,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        String reason) {
}
