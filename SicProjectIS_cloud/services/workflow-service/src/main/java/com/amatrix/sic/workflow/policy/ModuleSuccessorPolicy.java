package com.amatrix.sic.workflow.policy;

import org.springframework.stereotype.Component;

@Component
public class ModuleSuccessorPolicy {

    public Completion completion(String moduleType, String finalState) {
        String key = normalized(moduleType) + ":" + normalized(finalState);
        return switch (key) {
            case "APPLICATION:APPLICATION_APPROVED" -> new Completion("CONTRACT", "CONTRACT");
            case "CONTRACT:CONTRACT_APPROVED" -> new Completion("ACCEPTANCE", "ACCEPTANCE");
            case "ACCEPTANCE:ACCEPTANCE_ACCEPTED" -> new Completion(null, "PROJECT_FINISHED");
            case "ACCEPTANCE:ACCEPTANCE_REJECTED" -> new Completion(null, "ACCEPTANCE_REJECTED");
            default -> new Completion(null, normalized(moduleType) + "_FINISHED");
        };
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record Completion(String nextModuleType, String lifecycleStage) {
        public boolean hasSuccessor() {
            return nextModuleType != null && !nextModuleType.isBlank();
        }
    }
}
