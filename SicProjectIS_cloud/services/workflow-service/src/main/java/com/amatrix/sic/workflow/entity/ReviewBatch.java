package com.amatrix.sic.workflow.entity;

public record ReviewBatch(Long id, Long moduleInstanceId, Long workflowNodeId, int round, String status, Long createdBy) {}
