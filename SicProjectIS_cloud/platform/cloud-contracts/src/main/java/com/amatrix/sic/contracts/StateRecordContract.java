package com.amatrix.sic.contracts;

import java.time.LocalDateTime;

public record StateRecordContract(
        Long stateRecordId,
        Long moduleInstanceId,
        Integer seq,
        Integer roundNo,
        String eventType,
        String fromState,
        String toState,
        String fromNodeId,
        String toNodeId,
        String result,
        String summary,
        LocalDateTime createdAt) {
}
