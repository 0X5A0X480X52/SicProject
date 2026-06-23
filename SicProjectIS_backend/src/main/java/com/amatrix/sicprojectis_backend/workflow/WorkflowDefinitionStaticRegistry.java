package com.amatrix.sicprojectis_backend.workflow;

import org.springframework.stereotype.Component;

import com.amatrix.sicprojectis_backend.runtime.statemachine.StateMachineExtensionRegistry;

@Component
public class WorkflowDefinitionStaticRegistry {
    private final StateMachineExtensionRegistry extensionRegistry;

    public WorkflowDefinitionStaticRegistry(StateMachineExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    public boolean actionKeyExists(String actionKey) {
        return extensionRegistry.actionKeyExists(actionKey);
    }

    public boolean validatorExists(String validatorKey) {
        return extensionRegistry.validatorExists(validatorKey);
    }

    public boolean conditionHandlerExists(String conditionHandlerKey) {
        return extensionRegistry.conditionHandlerExists(conditionHandlerKey);
    }
}
