package com.amatrix.sicprojectis_backend.runtime.statemachine;

import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.TransitionConfig;

public record TransitionContext(
        ProjectModuleInstance moduleInstance,
        ModuleStateRecord stateRecord,
        TransitionConfig transition) {
}
