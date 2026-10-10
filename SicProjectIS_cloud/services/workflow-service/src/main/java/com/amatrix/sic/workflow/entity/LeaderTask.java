package com.amatrix.sic.workflow.entity;

public record LeaderTask(long taskId, long projectId, String moduleType, int round, String nodeId) {}
