package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class StateMachineExtensionRegistry {
    private final Map<String, TransitionActionHandler> actionHandlers = new LinkedHashMap<>();
    private final Map<String, TransitionConditionHandler> conditionHandlers = new LinkedHashMap<>();
    private final Map<String, NodeMaterialValidator> validators = new LinkedHashMap<>();

    public StateMachineExtensionRegistry(List<TransitionActionHandler> actionHandlers,
            List<TransitionConditionHandler> conditionHandlers, List<NodeMaterialValidator> validators) {
        actionHandlers.forEach(handler -> handler.actionKeys().forEach(key -> this.actionHandlers.put(key, handler)));
        conditionHandlers.forEach(handler -> handler.conditionHandlerKeys().forEach(key -> this.conditionHandlers.put(key, handler)));
        validators.forEach(handler -> handler.validatorKeys().forEach(key -> this.validators.put(key, handler)));
    }

    public boolean actionKeyExists(String actionKey) {
        return actionKey == null || actionKey.isBlank() || actionHandlers.containsKey(actionKey);
    }

    public boolean conditionHandlerExists(String conditionHandlerKey) {
        return conditionHandlerKey == null || conditionHandlerKey.isBlank()
                || conditionHandlers.containsKey(conditionHandlerKey);
    }

    public boolean validatorExists(String validatorKey) {
        return validatorKey == null || validatorKey.isBlank() || validators.containsKey(validatorKey);
    }

    public TransitionActionHandler actionHandler(String actionKey) {
        return actionHandlers.get(actionKey);
    }

    public TransitionConditionHandler conditionHandler(String conditionHandlerKey) {
        return conditionHandlers.get(conditionHandlerKey);
    }

    public NodeMaterialValidator validator(String validatorKey) {
        return validators.get(validatorKey);
    }

    public Set<String> actionKeys() {
        return actionHandlers.keySet();
    }

    public Set<String> conditionHandlerKeys() {
        return conditionHandlers.keySet();
    }

    public Set<String> validatorKeys() {
        return validators.keySet();
    }
}
