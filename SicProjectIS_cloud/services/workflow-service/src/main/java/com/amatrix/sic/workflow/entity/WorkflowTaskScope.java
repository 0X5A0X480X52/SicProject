package com.amatrix.sic.workflow.entity;

public record WorkflowTaskScope(Long taskId, Long moduleId, Long projectId, String moduleType,
        String nodeId, Long assigneeUserId, String candidateRole, int round) {}
