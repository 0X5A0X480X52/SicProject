package com.amatrix.sicprojectis_backend.runtime.statemachine;

import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;

public record TransitionActionContext(
        ProjectModuleInstance moduleInstance,
        ModuleStateRecord stateRecord,
        TransitionConfig transition,
        WorkflowNode completedWorkflowNode,
        WorkflowNode targetWorkflowNode,
        NodeConfig targetNode,
        boolean finished) {
}
