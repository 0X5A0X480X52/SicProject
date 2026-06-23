package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;

@Component
public class DefaultTransitionActionHandler implements TransitionActionHandler {
    public static final Set<String> OFFICIAL_ACTION_KEYS = Set.of(
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

    private final ProcessDocumentGenerationService documentGenerationService;
    private final ProjectDao projectDao;

    public DefaultTransitionActionHandler(ProcessDocumentGenerationService documentGenerationService, ProjectDao projectDao) {
        this.documentGenerationService = documentGenerationService;
        this.projectDao = projectDao;
    }

    @Override
    public Set<String> actionKeys() {
        return OFFICIAL_ACTION_KEYS;
    }

    @Override
    public void execute(String actionKey, TransitionActionContext context) {
        if (actionKey == null || "SYSTEM_AUTO".equals(actionKey)) {
            return;
        }
        if (actionKey.startsWith("CREATE_")) {
            validateTargetTask(actionKey, context);
            return;
        }
        if (actionKey.startsWith("GENERATE_")) {
            documentGenerationService.generateForNodeComplete(context.moduleInstance(), context.completedWorkflowNode(),
                    context.stateRecord());
            if (context.finished()) {
                documentGenerationService.generateForProcessEnd(context.moduleInstance(), context.targetWorkflowNode(),
                        context.stateRecord());
                updateProjectLifecycle(context);
            }
        }
    }

    private void validateTargetTask(String actionKey, TransitionActionContext context) {
        if (context.finished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    actionKey + " cannot create task because target is process end");
        }
        if (context.targetNode() == null || context.targetNode().candidateRoleCode() == null
                || context.targetNode().candidateRoleCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    actionKey + " target node has no candidateRoleCode");
        }
    }

    private void updateProjectLifecycle(TransitionActionContext context) {
        Project project = projectDao.selectById(context.moduleInstance().getProjectId());
        if (project == null) {
            return;
        }
        project.setLifecycleStage(context.moduleInstance().getModuleType() + "_FINISHED");
        project.setUpdatedAt(LocalDateTime.now());
        projectDao.updateById(project);
    }
}
