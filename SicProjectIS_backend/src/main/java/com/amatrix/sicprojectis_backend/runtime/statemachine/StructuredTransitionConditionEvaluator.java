package com.amatrix.sicprojectis_backend.runtime.statemachine;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordRemarkDao;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dao.RuntimeStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.TransitionConfig;

@Service
public class StructuredTransitionConditionEvaluator {
    private static final List<String> POSITIVE_RESULTS = List.of(
            "APPROVED", "PASS", "PASSED", "RECOMMENDED", "ACCEPTED", "PROCESS_COMPLETED", "SCHOOL_LEVEL");

    private final RuntimeStructuredDataDao runtimeDao;
    private final ProjectStructuredDataDao projectStructuredDao;
    private final ExpertReviewBatchDao expertBatchDao;
    private final StateRecordRemarkDao remarkDao;
    private final ProjectApplicationDao applicationDao;
    private final ProjectAcceptanceDao acceptanceDao;
    private final StateMachineExtensionRegistry extensionRegistry;

    public StructuredTransitionConditionEvaluator(RuntimeStructuredDataDao runtimeDao,
            ProjectStructuredDataDao projectStructuredDao, ExpertReviewBatchDao expertBatchDao,
            StateRecordRemarkDao remarkDao, ProjectApplicationDao applicationDao, ProjectAcceptanceDao acceptanceDao,
            StateMachineExtensionRegistry extensionRegistry) {
        this.runtimeDao = runtimeDao;
        this.projectStructuredDao = projectStructuredDao;
        this.expertBatchDao = expertBatchDao;
        this.remarkDao = remarkDao;
        this.applicationDao = applicationDao;
        this.acceptanceDao = acceptanceDao;
        this.extensionRegistry = extensionRegistry;
    }

    public boolean matches(TransitionContext context) {
        TransitionConfig transition = context.transition();
        String conditionType = normalize(transition.conditionType());
        if (conditionType == null || "NONE".equals(conditionType) || "ALWAYS".equals(conditionType)) {
            return true;
        }
        if ("CUSTOM".equals(conditionType) || transition.conditionHandlerKey() != null) {
            TransitionConditionHandler handler = extensionRegistry.conditionHandler(transition.conditionHandlerKey());
            if (handler == null) {
                throw new IllegalArgumentException("Unknown conditionHandlerKey: " + transition.conditionHandlerKey());
            }
            return handler.matches(context);
        }
        Object actual = structuredValue(context);
        if ("SIMPLE_BOOL".equals(conditionType)) {
            return asBoolean(actual) == Boolean.parseBoolean(transition.conditionValue());
        }
        if ("EQUALS".equals(conditionType)) {
            return Objects.equals(stringValue(actual), transition.conditionValue());
        }
        if ("NOT_EQUALS".equals(conditionType)) {
            return !Objects.equals(stringValue(actual), transition.conditionValue());
        }
        throw new IllegalArgumentException("Unsupported conditionType: " + transition.conditionType());
    }

    private Object structuredValue(TransitionContext context) {
        String key = Objects.requireNonNullElse(context.transition().conditionKey(), "");
        String normalized = key.toLowerCase(Locale.ROOT);
        if ("islimitedproject".equals(normalized)) {
            var application = applicationDao.selectByProjectId(context.moduleInstance().getProjectId());
            if (application == null) {
                return false;
            }
            var extension = projectStructuredDao.selectApplicationExtByApplicationId(application.getApplicationId());
            return extension != null && Boolean.TRUE.equals(extension.getIsLimitedProject());
        }
        if ("isschoollevelacceptance".equals(normalized)) {
            var acceptance = acceptanceDao.selectByProjectId(context.moduleInstance().getProjectId());
            if (acceptance == null) {
                return false;
            }
            var extension = projectStructuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId());
            return extension != null && Boolean.TRUE.equals(extension.getIsSchoolLevelAcceptance());
        }
        if (normalized.endsWith("expertapproved") || normalized.contains("expert")) {
            return expertBatchDao.selectByModuleInstanceId(context.moduleInstance().getModuleInstanceId()).stream()
                    .max(Comparator.comparing(batch -> batch.getBatchId()))
                    .map(batch -> approved(batch.getFinalResult()))
                    .orElse(false);
        }
        if (normalized.endsWith("authorityapproved") || normalized.contains("authority")) {
            return latestExternalResultApproved(context) || currentRemarkApproved(context);
        }
        if (normalized.endsWith("approved") || normalized.contains("publicity")) {
            return currentRemarkApproved(context) || currentCheckItemsPassed(context);
        }
        throw new IllegalArgumentException("Unknown conditionKey: " + key);
    }

    private boolean latestExternalResultApproved(TransitionContext context) {
        return runtimeDao.selectExternalResultsByModuleInstanceId(context.moduleInstance().getModuleInstanceId()).stream()
                .max(Comparator.comparing(ExternalResultRecord::getExternalResultId))
                .map(row -> approved(row.getExternalResult()))
                .orElse(false);
    }

    private boolean currentRemarkApproved(TransitionContext context) {
        return remarkDao.selectAll().stream()
                .filter(row -> Objects.equals(row.getStateRecordId(), context.stateRecord().getStateRecordId()))
                .filter(row -> Boolean.TRUE.equals(row.getIsFinal()))
                .map(row -> approved(row.getResult()))
                .findFirst()
                .orElse(false);
    }

    private boolean currentCheckItemsPassed(TransitionContext context) {
        var items = runtimeDao.selectCheckItemsByStateRecordId(context.stateRecord().getStateRecordId());
        return !items.isEmpty() && items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequired()))
                .allMatch(item -> Boolean.TRUE.equals(item.getPassed()));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return approved(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean approved(String result) {
        return result != null && POSITIVE_RESULTS.contains(result.toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
