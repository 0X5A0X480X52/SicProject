package com.amatrix.sicprojectis_backend.project.dto;

public record AssignProjectExpertRequest(
        Long userId,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        String reason) {
}
