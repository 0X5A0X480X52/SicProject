package com.amatrix.sic.workflow.entity;

public record NodeFormModule(Long projectId, String moduleType, String nodeId, String state, int round, boolean finished) {}
