package com.amatrix.sic.workflow.entity;

public record ExpertGrantScope(Long projectId, String moduleType, int round, Long createdBy,
        String nodeId, String expertName) {}
