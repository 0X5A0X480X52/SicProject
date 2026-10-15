package com.amatrix.sic.contracts;

/**
 * 一次迁移响应：状态记录、当前节点/状态、是否结束。
 */
public record StateTransitionContract(
        StateRecordContract stateRecord,
        String currentNodeId,
        String currentState,
        boolean finished) {
}
