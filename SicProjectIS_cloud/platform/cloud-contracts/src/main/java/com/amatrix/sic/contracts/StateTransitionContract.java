package com.amatrix.sic.contracts;

public record StateTransitionContract(
        StateRecordContract stateRecord,
        String currentNodeId,
        String currentState,
        boolean finished) {
}
