package com.amatrix.sicprojectis_backend.runtime.statemachine.dto;

import java.util.List;

import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;

public record StateTransitionRequest(
        String eventType,
        Integer expectedSeq,
        String result,
        String remark,
        List<Long> materialVersionIds,
        String formCode,
        NodeFormSaveRequest nodeFormData) {
}
