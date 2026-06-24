package com.amatrix.sicprojectis_backend.runtime.statemachine.dto;

import java.util.List;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;

public record RuntimeViewResponse(
        ModuleRuntimeContextView context,
        boolean canOperate,
        List<AvailableTransition> availableTransitions,
        List<MaterialRequirementView> materialRequirements,
        List<NodeFormDefinition> nodeForms,
        List<TaskInstance> openTasks,
        List<ModuleStateRecord> history) {

    public record AvailableTransition(
            String transitionId,
            String eventType,
            String result,
            String targetRef,
            String targetStateCode,
            String conditionType,
            String conditionKey,
            String conditionValue,
            String conditionHandlerKey,
            List<String> actionKeys) {
    }

    public record MaterialRequirementView(
            Long requirementId,
            String materialTypeCode,
            String materialTypeName,
            Boolean required,
            Integer minCount,
            Integer maxCount,
            String usageType,
            String validatorKey,
            String description) {
    }
}
