package com.amatrix.sic.contracts;

import java.util.UUID;

public record ApplicationSagaResult(
        UUID sagaId,
        Long projectId,
        Long moduleInstanceId,
        StateTransitionContract transition,
        String status) {
}
