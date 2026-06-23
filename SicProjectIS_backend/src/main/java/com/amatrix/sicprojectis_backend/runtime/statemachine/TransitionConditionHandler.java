package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.Set;

public interface TransitionConditionHandler {
    default String conditionHandlerKey() {
        return null;
    }

    default Set<String> conditionHandlerKeys() {
        String key = conditionHandlerKey();
        return key == null ? Set.of() : Set.of(key);
    }

    boolean matches(TransitionContext context);
}
