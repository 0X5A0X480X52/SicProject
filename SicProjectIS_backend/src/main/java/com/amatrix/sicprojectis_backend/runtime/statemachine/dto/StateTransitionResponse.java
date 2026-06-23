package com.amatrix.sicprojectis_backend.runtime.statemachine.dto;

import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;

public record StateTransitionResponse(
        ModuleStateRecord stateRecord,
        String currentNodeId,
        String currentState,
        boolean finished) {
}
