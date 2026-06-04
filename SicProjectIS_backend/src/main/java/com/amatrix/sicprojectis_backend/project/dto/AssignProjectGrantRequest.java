package com.amatrix.sicprojectis_backend.project.dto;

public record AssignProjectGrantRequest(
        Long userId,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        String reason) {
}
