package com.amatrix.sic.workflow.entity;
public record WorkflowSagaState(Long moduleId,Long projectId,String node,String state,boolean finished,Long stateId,
    int seq,int round,String event,String fromState,String toState,String fromNode,String toNode,String result,String summary,java.time.LocalDateTime createdAt) {}
