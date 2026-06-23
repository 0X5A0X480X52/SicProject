package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.List;
import java.util.Set;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeMaterialRequirement;

public interface NodeMaterialValidator {
    default String validatorKey() {
        return null;
    }

    default Set<String> validatorKeys() {
        String key = validatorKey();
        return key == null ? Set.of() : Set.of(key);
    }

    void validate(Long projectId, WorkflowNode workflowNode, WorkflowNodeMaterialRequirement requirement,
            List<Long> materialVersionIds);
}
