package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.material.dao.MaterialContextViewDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialTypeDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeMaterialRequirementDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeMaterialRequirement;

@Service
public class NodeMaterialValidationService {
    private final WorkflowNodeMaterialRequirementDao requirementDao;
    private final MaterialTypeDao materialTypeDao;
    private final MaterialContextViewDao materialContextViewDao;
    private final StateMachineExtensionRegistry extensionRegistry;

    public NodeMaterialValidationService(WorkflowNodeMaterialRequirementDao requirementDao, MaterialTypeDao materialTypeDao,
            MaterialContextViewDao materialContextViewDao, StateMachineExtensionRegistry extensionRegistry) {
        this.requirementDao = requirementDao;
        this.materialTypeDao = materialTypeDao;
        this.materialContextViewDao = materialContextViewDao;
        this.extensionRegistry = extensionRegistry;
    }

    public void validateBeforeSubmit(Long projectId, WorkflowNode workflowNode, List<Long> materialVersionIds) {
        if (workflowNode == null || workflowNode.getWorkflowNodeId() == null) {
            return;
        }
        List<Long> submittedIds = materialVersionIds == null ? List.of()
                : materialVersionIds.stream().filter(Objects::nonNull).distinct().toList();
        for (WorkflowNodeMaterialRequirement requirement : requirementDao.selectByWorkflowNodeId(workflowNode.getWorkflowNodeId())) {
            if (!appliesBeforeSubmit(requirement)) {
                continue;
            }
            var materialType = materialTypeDao.selectById(requirement.getMaterialTypeId());
            if (materialType == null) {
                continue;
            }
            long count = submittedIds.stream()
                    .map(materialContextViewDao::selectByMaterialVersionId)
                    .filter(Objects::nonNull)
                    .filter(version -> Objects.equals(version.getProjectId(), projectId))
                    .filter(version -> Objects.equals(version.getMaterialTypeId(), requirement.getMaterialTypeId()))
                    .filter(version -> Boolean.TRUE.equals(version.getIsCurrent()))
                    .count();
            int minCount = requirement.getMinCount() == null ? 0 : requirement.getMinCount();
            if (Boolean.TRUE.equals(requirement.getRequired()) && count < Math.max(1, minCount)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Material is required: " + materialType.getMaterialTypeCode());
            }
            if (requirement.getMaxCount() != null && count > requirement.getMaxCount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Too many material versions for: " + materialType.getMaterialTypeCode());
            }
            if (requirement.getValidatorKey() != null && !requirement.getValidatorKey().isBlank()) {
                NodeMaterialValidator validator = extensionRegistry.validator(requirement.getValidatorKey());
                if (validator == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown validatorKey: " + requirement.getValidatorKey());
                }
                validator.validate(projectId, workflowNode, requirement, submittedIds);
            }
        }
    }

    private boolean appliesBeforeSubmit(WorkflowNodeMaterialRequirement requirement) {
        String timing = requirement.getRequirementTiming();
        return timing == null || timing.isBlank() || "BEFORE_SUBMIT".equalsIgnoreCase(timing);
    }
}
