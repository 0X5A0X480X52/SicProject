package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordRemarkDao;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dao.RuntimeStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;

@Component
public class CommonTransitionConditionHandler implements TransitionConditionHandler {
    private static final List<String> POSITIVE_RESULTS = List.of(
            "APPROVED", "PASS", "PASSED", "RECOMMENDED", "ACCEPTED", "PROCESS_COMPLETED", "SCHOOL_LEVEL");
    private static final Set<String> KEYS = Set.of(
            "EXPERT_REVIEW_PASS_CONDITION",
            "EXTERNAL_RESULT_PASS_CONDITION",
            "CHECK_ITEM_PASS_CONDITION",
            "LIMITED_PROJECT_CONDITION",
            "SCHOOL_LEVEL_ACCEPTANCE_CONDITION");

    private final RuntimeStructuredDataDao runtimeDao;
    private final ProjectStructuredDataDao projectStructuredDao;
    private final ExpertReviewBatchDao expertBatchDao;
    private final StateRecordRemarkDao remarkDao;
    private final ProjectApplicationDao applicationDao;
    private final ProjectAcceptanceDao acceptanceDao;

    public CommonTransitionConditionHandler(RuntimeStructuredDataDao runtimeDao,
            ProjectStructuredDataDao projectStructuredDao, ExpertReviewBatchDao expertBatchDao,
            StateRecordRemarkDao remarkDao, ProjectApplicationDao applicationDao, ProjectAcceptanceDao acceptanceDao) {
        this.runtimeDao = runtimeDao;
        this.projectStructuredDao = projectStructuredDao;
        this.expertBatchDao = expertBatchDao;
        this.remarkDao = remarkDao;
        this.applicationDao = applicationDao;
        this.acceptanceDao = acceptanceDao;
    }

    @Override
    public Set<String> conditionHandlerKeys() {
        return KEYS;
    }

    @Override
    public boolean matches(TransitionContext context) {
        String key = context.transition().conditionHandlerKey();
        if ("EXPERT_REVIEW_PASS_CONDITION".equals(key)) {
            Integer roundNo = context.stateRecord() == null ? null : context.stateRecord().getRoundNo();
            return expertBatchDao.selectByModuleInstanceId(context.moduleInstance().getModuleInstanceId()).stream()
                    .filter(batch -> roundNo == null || batch.getRoundNo() == null || java.util.Objects.equals(batch.getRoundNo(), roundNo))
                    .max(Comparator.comparing(batch -> batch.getBatchId()))
                    .map(batch -> approved(batch.getFinalResult()))
                    .orElse(false);
        }
        if ("EXTERNAL_RESULT_PASS_CONDITION".equals(key)) {
            return runtimeDao.selectExternalResultsByModuleInstanceId(context.moduleInstance().getModuleInstanceId()).stream()
                    .max(Comparator.comparing(ExternalResultRecord::getExternalResultId))
                    .map(row -> approved(row.getExternalResult()))
                    .orElse(false);
        }
        if ("CHECK_ITEM_PASS_CONDITION".equals(key)) {
            var items = runtimeDao.selectCheckItemsByStateRecordId(context.stateRecord().getStateRecordId());
            return !items.isEmpty() && items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getRequired()))
                    .allMatch(item -> Boolean.TRUE.equals(item.getPassed()))
                    || currentRemarkApproved(context);
        }
        if ("LIMITED_PROJECT_CONDITION".equals(key)) {
            var application = applicationDao.selectByProjectId(context.moduleInstance().getProjectId());
            if (application == null) return false;
            var extension = projectStructuredDao.selectApplicationExtByApplicationId(application.getApplicationId());
            return extension != null && Boolean.TRUE.equals(extension.getIsLimitedProject());
        }
        if ("SCHOOL_LEVEL_ACCEPTANCE_CONDITION".equals(key)) {
            var acceptance = acceptanceDao.selectByProjectId(context.moduleInstance().getProjectId());
            if (acceptance == null) return false;
            var extension = projectStructuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId());
            return extension != null && Boolean.TRUE.equals(extension.getIsSchoolLevelAcceptance());
        }
        return false;
    }

    private boolean currentRemarkApproved(TransitionContext context) {
        return remarkDao.selectAll().stream()
                .filter(row -> java.util.Objects.equals(row.getStateRecordId(), context.stateRecord().getStateRecordId()))
                .filter(row -> Boolean.TRUE.equals(row.getIsFinal()))
                .map(row -> approved(row.getResult()))
                .findFirst()
                .orElse(false);
    }

    private boolean approved(String result) {
        return result != null && POSITIVE_RESULTS.contains(result.toUpperCase(Locale.ROOT));
    }
}
