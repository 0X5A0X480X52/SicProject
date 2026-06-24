package com.amatrix.sicprojectis_backend.project.dto;

import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionResponse;

public record StartProjectApplicationResponse(
        Long projectId,
        Long moduleInstanceId,
        StateTransitionResponse transition) {
}
