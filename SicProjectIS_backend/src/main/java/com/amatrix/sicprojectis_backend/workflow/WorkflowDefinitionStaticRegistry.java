package com.amatrix.sicprojectis_backend.workflow;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitionStaticRegistry {
    private static final Set<String> ACTION_KEYS = Set.of(
            "CHECK_ACCEPTANCE_PROJECT_TYPE",
            "CHECK_LIMITED_PROJECT",
            "CREATE_ACCEPTANCE_CERTIFICATE_TASK",
            "CREATE_ACCEPTANCE_FAIL_FILE_TASK",
            "CREATE_ACCEPTANCE_FINAL_SUBMIT_TASK",
            "CREATE_ACCEPTANCE_MATERIAL_SUBMIT_TASK",
            "CREATE_ACCEPTANCE_SIGN_SEAL_TASK",
            "CREATE_APPLICATION_DRAFT_TASK",
            "CREATE_AUTHORITY_ACCEPTANCE_REVIEW_TASK",
            "CREATE_AUTHORITY_CONTRACT_REVIEW_TASK",
            "CREATE_AUTHORITY_REVIEW_RESULT_TASK",
            "CREATE_AUTHORITY_SEAL_TASK",
            "CREATE_AUTHORITY_SUBMISSION_TASK",
            "CREATE_CONTRACT_ARCHIVE_TASK",
            "CREATE_CONTRACT_FILL_TASK",
            "CREATE_DEPT_ACCEPTANCE_REVIEW_TASK",
            "CREATE_DEPT_CONTRACT_REVIEW_TASK",
            "CREATE_DEPT_EXPERT_REVIEW_TASK",
            "CREATE_DEPT_NOTIFY_TASK",
            "CREATE_DEPT_REVIEW_TASK",
            "CREATE_EXPERT_ACCEPTANCE_REVIEW_TASK",
            "CREATE_FINAL_MATERIAL_SUBMIT_TASK",
            "CREATE_FINANCIAL_SETTLEMENT_TASK",
            "CREATE_LEADER_SIGN_TASK",
            "CREATE_PRINT_CONTRACT_TASK",
            "CREATE_PUBLICITY_TASK",
            "CREATE_SCHOOL_SEAL_TASK",
            "CREATE_SCIENCE_ACCEPTANCE_REVIEW_TASK",
            "CREATE_SCIENCE_CONTRACT_REVIEW_TASK",
            "CREATE_SCIENCE_EXPERT_REVIEW_TASK",
            "CREATE_SCIENCE_INITIAL_REVIEW_TASK",
            "CREATE_SIGN_AND_SEAL_TASK",
            "CREATE_SURPLUS_FUNDS_RETURN_TASK",
            "EVALUATE_AUTHORITY_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_AUTHORITY_CONTRACT_REVIEW_RESULT",
            "EVALUATE_AUTHORITY_REVIEW_RESULT",
            "EVALUATE_DEPT_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_DEPT_CONTRACT_REVIEW_RESULT",
            "EVALUATE_DEPT_EXPERT_REVIEW_RESULT",
            "EVALUATE_DEPT_REVIEW_RESULT",
            "EVALUATE_EXPERT_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_PUBLICITY_RESULT",
            "EVALUATE_SCIENCE_ACCEPTANCE_REVIEW_RESULT",
            "EVALUATE_SCIENCE_CONTRACT_REVIEW_RESULT",
            "EVALUATE_SCIENCE_EXPERT_REVIEW_RESULT",
            "EVALUATE_SCIENCE_INITIAL_REVIEW_RESULT",
            "GENERATE_ACCEPTANCE_DOCUMENTS",
            "GENERATE_ACCEPTANCE_FAIL_DOCUMENTS",
            "GENERATE_APPLICATION_DOCUMENTS",
            "GENERATE_CONTRACT_DOCUMENTS",
            "RETURN_ACCEPTANCE_TO_LEADER",
            "RETURN_CONTRACT_TO_LEADER",
            "RETURN_TO_APPLICANT",
            "SYSTEM_AUTO");

    public boolean actionKeyExists(String actionKey) {
        return ACTION_KEYS.contains(actionKey);
    }

    public boolean validatorExists(String validatorKey) {
        return true;
    }

    public boolean conditionHandlerExists(String conditionHandlerKey) {
        return true;
    }
}
