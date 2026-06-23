package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeMaterialRequirement;

@Component
public class DefaultNodeMaterialValidator implements NodeMaterialValidator {
    private static final Set<String> KEYS = Set.of(
            "NOOP",
            "BUDGET_DETAIL_VALIDATOR",
            "CONTRACT_FILE_VALIDATOR",
            "ACCEPTANCE_MATERIAL_VALIDATOR",
            "FINAL_SUBMISSION_MATERIAL_VALIDATOR");

    @Override
    public Set<String> validatorKeys() {
        return KEYS;
    }

    @Override
    public void validate(Long projectId, WorkflowNode workflowNode, WorkflowNodeMaterialRequirement requirement,
            List<Long> materialVersionIds) {
        // First version: semantic validators are registration points; common min/max/current checks run before this.
    }
}
