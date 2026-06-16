package com.amatrix.sicprojectis_backend.structured.dto;

import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;

public record TransitionResponse(ModuleStateRecord stateRecord, String currentNodeId, String currentState, boolean finished) {
}
