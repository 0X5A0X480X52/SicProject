package com.amatrix.sic.contracts;

public record ProjectOperationTarget(
        String decisionId,
        Long projectId,
        String operation,
        String moduleType,
        Integer roundNo,
        String taskNodeId) {
}
