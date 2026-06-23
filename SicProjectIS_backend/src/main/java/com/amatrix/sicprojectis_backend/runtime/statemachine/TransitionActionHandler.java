package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.Set;

public interface TransitionActionHandler {
    default String actionKey() {
        return null;
    }

    default Set<String> actionKeys() {
        String key = actionKey();
        return key == null ? Set.of() : Set.of(key);
    }

    void execute(String actionKey, TransitionActionContext context);
}
