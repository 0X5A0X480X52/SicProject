package com.amatrix.sic.workflow.entity;

import java.time.LocalDateTime;
public record ModuleRecord(Long id, Long projectId, Long startedByUserId, String type, Long definitionId,
        String nodeId, String state, int seq, int round, boolean finished, LocalDateTime createdAt, LocalDateTime updatedAt) {}
