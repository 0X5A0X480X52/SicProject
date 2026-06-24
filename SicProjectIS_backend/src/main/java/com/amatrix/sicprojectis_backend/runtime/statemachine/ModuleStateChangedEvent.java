package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.time.LocalDateTime;

public record ModuleStateChangedEvent(
        Long projectId,
        Long moduleInstanceId,
        String moduleType,
        String fromState,
        String toState,
        Integer seq,
        String eventType,
        LocalDateTime occurredAt) {
}
