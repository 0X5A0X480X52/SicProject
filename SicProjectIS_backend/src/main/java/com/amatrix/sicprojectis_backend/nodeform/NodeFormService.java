package com.amatrix.sicprojectis_backend.nodeform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.expert.ExpertReviewService;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.expert.dto.ExpertReviewBatchDetailResponse;
import com.amatrix.sicprojectis_backend.material.MaterialService;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormContext;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataKind;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataResponse;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormExpertRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormProjectRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRecordResponse;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRegistry;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRuntimeRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormWriteMode;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.structured.NoticeService;
import com.amatrix.sicprojectis_backend.structured.StructuredBusinessService;
import com.amatrix.sicprojectis_backend.structured.dao.NoticeRecordDao;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dao.RuntimeStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dto.FinancialSettlementRequest;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;

@Service
public class NodeFormService {
    private final NodeFormRegistry registry;
    private final StructuredBusinessService structuredBusinessService;
    private final NoticeService noticeService;
    private final NoticeRecordDao noticeDao;
    private final RuntimeStructuredDataDao runtimeDao;
    private final ProjectStructuredDataDao projectStructuredDao;
    private final ProjectDao projectDao;
    private final ProjectApplicationDao applicationDao;
    private final ProjectAcceptanceDao acceptanceDao;
    private final PermissionService permissionService;
    private final ExpertReviewService expertReviewService;
    private final ExpertReviewBatchDao expertReviewBatchDao;
    private final MaterialService materialService;

    public NodeFormService(NodeFormRegistry registry, StructuredBusinessService structuredBusinessService,
            NoticeService noticeService, NoticeRecordDao noticeDao, RuntimeStructuredDataDao runtimeDao,
            ProjectStructuredDataDao projectStructuredDao, ProjectDao projectDao, ProjectApplicationDao applicationDao,
            ProjectAcceptanceDao acceptanceDao, PermissionService permissionService,
            ExpertReviewService expertReviewService,
            ExpertReviewBatchDao expertReviewBatchDao, MaterialService materialService) {
        this.registry = registry;
        this.structuredBusinessService = structuredBusinessService;
        this.noticeService = noticeService;
        this.noticeDao = noticeDao;
        this.runtimeDao = runtimeDao;
        this.projectStructuredDao = projectStructuredDao;
        this.projectDao = projectDao;
        this.applicationDao = applicationDao;
        this.acceptanceDao = acceptanceDao;
        this.permissionService = permissionService;
        this.expertReviewService = expertReviewService;
        this.expertReviewBatchDao = expertReviewBatchDao;
        this.materialService = materialService;
    }

    public List<NodeFormDefinition> definitions() {
        return registry.definitions();
    }

    public NodeFormDataResponse get(AuthenticatedUser user, String formCode, NodeFormContext context) {
        NodeFormDefinition definition = registry.require(formCode);
        requireProjectAccessIfPresent(user, context.projectId());
        List<MaterialContextView> materials = context.projectId() == null ? List.of()
                : materialService.listProjectMaterials(user, context.projectId());
        return new NodeFormDataResponse(definition, context,
                definition.dataKind() == NodeFormDataKind.APPLICATION_DRAFT && context.projectId() != null
                        ? structuredBusinessService.getApplication(user, context.projectId())
                        : null,
                definition.dataKind() == NodeFormDataKind.CONTRACT_DRAFT && context.projectId() != null
                        ? structuredBusinessService.getContract(user, context.projectId())
                        : null,
                definition.dataKind() == NodeFormDataKind.ACCEPTANCE_DRAFT && context.projectId() != null
                        ? structuredBusinessService.getAcceptance(user, context.projectId())
                        : null,
                definition.dataKind() == NodeFormDataKind.NOTICE
                        ? noticeDao.selectByModuleType(definition.moduleType().name())
                        : List.of(),
                loadCheckItems(context),
                definition.dataKind() == NodeFormDataKind.EXTERNAL_RESULT ? loadExternalResults(context) : List.of(),
                definition.dataKind() == NodeFormDataKind.SEAL ? loadSealRecords(context) : List.of(),
                definition.dataKind() == NodeFormDataKind.SUBMISSION ? loadSubmissionRecords(context) : List.of(),
                definition.dataKind() == NodeFormDataKind.ARCHIVE ? loadArchiveRecords(context) : List.of(),
                definition.dataKind() == NodeFormDataKind.PUBLICITY && context.projectId() != null
                        ? projectStructuredDao.selectPublicitiesByProjectId(context.projectId())
                        : List.of(),
                definition.dataKind() == NodeFormDataKind.FINANCIAL_SETTLEMENT && context.projectId() != null
                        ? projectStructuredDao.selectFinancialSettlementsByProjectId(context.projectId())
                        : List.of(),
                definition.dataKind() == NodeFormDataKind.ACHIEVEMENT && context.projectId() != null
                        ? projectStructuredDao.selectAchievementsByProjectId(context.projectId())
                        : List.of(),
                definition.dataKind() == NodeFormDataKind.SURPLUS_RETURN && context.projectId() != null
                        ? projectStructuredDao.selectSurplusReturnsByProjectId(context.projectId())
                        : List.of(),
                loadExpertReview(context),
                materials);
    }

    @Transactional
    public NodeFormDataResponse save(AuthenticatedUser user, String formCode, NodeFormSaveRequest request) {
        NodeFormDefinition definition = registry.require(formCode);
        if (definition.writeMode() != NodeFormWriteMode.SINGLE_INSTANCE) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Use records API for history forms");
        }
        Long projectId = requireProjectId(request);
        return switch (definition.dataKind()) {
            case APPLICATION_DRAFT -> {
                structuredBusinessService.saveApplication(user, projectId, request.applicationDraft());
                yield get(user, formCode, context(request));
            }
            case CONTRACT_DRAFT -> {
                structuredBusinessService.saveContract(user, projectId, request.contractDraft());
                yield get(user, formCode, context(request));
            }
            case ACCEPTANCE_DRAFT -> {
                structuredBusinessService.saveAcceptance(user, projectId, request.acceptanceDraft());
                yield get(user, formCode, context(request));
            }
            default ->
                throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "This form is not single-instance");
        };
    }

    @Transactional
    public NodeFormRecordResponse createRecord(AuthenticatedUser user, String formCode, NodeFormSaveRequest request) {
        NodeFormDefinition definition = registry.require(formCode);
        if (definition.writeMode() == NodeFormWriteMode.SINGLE_INSTANCE
                || definition.writeMode() == NodeFormWriteMode.READ_ONLY) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                    "This form does not support record creation");
        }
        validateRequiredContext(user, definition, request);
        Long recordId = createHistoryRecord(user, definition, request);
        return new NodeFormRecordResponse(definition, recordId, get(user, formCode, context(request)));
    }

    @Transactional
    public void saveForTransition(AuthenticatedUser user, String formCode, Long projectId, Long moduleInstanceId,
            Long stateRecordId, NodeFormSaveRequest request) {
        if (formCode == null || formCode.isBlank()) {
            return;
        }
        NodeFormDefinition definition = registry.require(formCode);
        NodeFormSaveRequest patched = withRuntimeContext(request, projectId, moduleInstanceId, stateRecordId);
        if (definition.writeMode() == NodeFormWriteMode.SINGLE_INSTANCE) {
            save(user, formCode, patched);
            return;
        }
        if (definition.writeMode() == NodeFormWriteMode.HISTORY_RECORD) {
            validateRequiredContext(user, definition, patched);
            createHistoryRecord(user, definition, patched);
            return;
        }
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "This form cannot be saved during transition");
    }

    @Transactional
    public NodeFormRecordResponse updateRecord(AuthenticatedUser user, String formCode, Long recordId,
            NodeFormSaveRequest request) {
        NodeFormDefinition definition = registry.require(formCode);
        if (definition.writeMode() != NodeFormWriteMode.HISTORY_RECORD) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                    "This form does not support record update");
        }
        validateRequiredContext(user, definition, request);
        updateHistoryRecord(user, definition, recordId, request);
        return new NodeFormRecordResponse(definition, recordId, get(user, formCode, context(request)));
    }

    @Transactional
    public void deleteRecord(AuthenticatedUser user, String formCode, Long recordId, NodeFormContext context) {
        NodeFormDefinition definition = registry.require(formCode);
        requireProjectAccessIfPresent(user, context.projectId());
        switch (definition.dataKind()) {
            case NOTICE -> noticeDao.deleteById(recordId);
            case CHECK_ITEM -> runtimeDao.deleteCheckItem(recordId);
            case EXTERNAL_RESULT -> runtimeDao.deleteExternalResult(recordId);
            case SEAL -> runtimeDao.deleteSealRecord(recordId);
            case SUBMISSION -> runtimeDao.deleteSubmissionRecord(recordId);
            case ARCHIVE -> runtimeDao.deleteArchiveRecord(recordId);
            case PUBLICITY -> projectStructuredDao.deletePublicity(recordId);
            case FINANCIAL_SETTLEMENT -> projectStructuredDao.deleteFinancialSettlement(recordId);
            case ACHIEVEMENT -> projectStructuredDao.deleteAchievement(recordId);
            case SURPLUS_RETURN -> projectStructuredDao.deleteSurplusReturn(recordId);
            default -> throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                    "This form does not support record delete");
        }
    }

    private Long createHistoryRecord(AuthenticatedUser user, NodeFormDefinition definition,
            NodeFormSaveRequest request) {
        return switch (definition.dataKind()) {
            case NOTICE -> noticeService.create(user, request.notice()).getNoticeId();
            case CHECK_ITEM -> insertCheckItem(definition, request);
            case EXTERNAL_RESULT -> insertExternalResult(user, definition, request);
            case SEAL -> insertSealRecord(user, definition, request);
            case SUBMISSION -> insertSubmissionRecord(user, definition, request);
            case ARCHIVE -> insertArchiveRecord(user, definition, request);
            case PUBLICITY -> insertPublicity(user, request);
            case FINANCIAL_SETTLEMENT -> structuredBusinessService.addSettlement(user, requireProjectId(request),
                    request.projectRecord().financialSettlement()).getSettlementId();
            case ACHIEVEMENT -> structuredBusinessService.addAchievement(user, requireProjectId(request),
                    request.projectRecord().achievement()).getAchievementId();
            case SURPLUS_RETURN -> insertSurplusReturn(user, request);
            case EXPERT_REVIEW -> upsertExpert(user, request);
            default -> throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Unsupported history form");
        };
    }

    private void updateHistoryRecord(AuthenticatedUser user, NodeFormDefinition definition, Long recordId,
            NodeFormSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        switch (definition.dataKind()) {
            case NOTICE -> noticeService.update(recordId, user, request.notice());
            case CHECK_ITEM -> {
                StateRecordCheckItem row = requireRuntime(request).checkItem();
                row.setCheckItemId(recordId);
                applyCheckItem(definition, request, row);
                runtimeDao.updateCheckItem(row);
            }
            case EXTERNAL_RESULT -> {
                ExternalResultRecord row = requireRuntime(request).externalResult();
                row.setExternalResultId(recordId);
                applyRuntime(definition, request, row);
                row.setUpdatedAt(now);
                runtimeDao.updateExternalResult(row);
            }
            case SEAL -> {
                SealRecord row = requireRuntime(request).sealRecord();
                row.setSealRecordId(recordId);
                applyRuntime(definition, request, row);
                row.setUpdatedAt(now);
                runtimeDao.updateSealRecord(row);
            }
            case SUBMISSION -> {
                SubmissionRecord row = requireRuntime(request).submissionRecord();
                row.setSubmissionId(recordId);
                applyRuntime(definition, request, row);
                row.setUpdatedAt(now);
                runtimeDao.updateSubmissionRecord(row);
            }
            case ARCHIVE -> {
                ArchiveRecord row = requireRuntime(request).archiveRecord();
                row.setArchiveId(recordId);
                applyRuntime(definition, request, row);
                row.setUpdatedAt(now);
                runtimeDao.updateArchiveRecord(row);
            }
            case PUBLICITY -> {
                ProjectApplicationPublicity row = requireProjectRecord(request).publicity();
                row.setPublicityId(recordId);
                row.setProjectId(requireProjectId(request));
                row.setUpdatedAt(now);
                projectStructuredDao.updatePublicity(row);
            }
            case FINANCIAL_SETTLEMENT ->
                projectStructuredDao.updateFinancialSettlement(settlement(recordId, user, request, now));
            case ACHIEVEMENT -> {
                var source = requireProjectRecord(request).achievement();
                var row = new com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement();
                row.setAchievementId(recordId);
                row.setProjectId(requireProjectId(request));
                row.setModuleInstanceId(source.moduleInstanceId());
                row.setAcceptanceId(source.acceptanceId());
                row.setAchievementType(source.achievementType());
                row.setAchievementTitle(source.achievementTitle());
                row.setAuthorList(source.authorList());
                row.setAchievementLevel(source.achievementLevel());
                row.setPublishOrGrantDate(source.publishOrGrantDate());
                row.setProofMaterialVersionId(source.proofMaterialVersionId());
                row.setRemark(source.remark());
                row.setUpdatedAt(now);
                projectStructuredDao.updateAchievement(row);
            }
            case SURPLUS_RETURN -> {
                SurplusFundsReturnRecord row = requireProjectRecord(request).surplusReturn();
                row.setReturnId(recordId);
                row.setProjectId(requireProjectId(request));
                row.setUpdatedAt(now);
                projectStructuredDao.updateSurplusReturn(row);
            }
            default -> throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Unsupported history form");
        }
    }

    private Long insertCheckItem(NodeFormDefinition definition, NodeFormSaveRequest request) {
        StateRecordCheckItem row = requireRuntime(request).checkItem();
        applyCheckItem(definition, request, row);
        row.setCreatedAt(LocalDateTime.now());
        runtimeDao.insertCheckItem(row);
        return row.getCheckItemId();
    }

    private Long insertExternalResult(AuthenticatedUser user, NodeFormDefinition definition,
            NodeFormSaveRequest request) {
        ExternalResultRecord row = requireRuntime(request).externalResult();
        applyRuntime(definition, request, row);
        LocalDateTime now = LocalDateTime.now();
        row.setResultType(defaultText(row.getResultType(), definition.formCode()));
        row.setExternalResult(defaultText(row.getExternalResult(), "APPROVED"));
        row.setRegisteredBy(user.userId());
        row.setRegisteredAt(row.getRegisteredAt() == null ? now : row.getRegisteredAt());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        runtimeDao.insertExternalResult(row);
        return row.getExternalResultId();
    }

    private Long insertSealRecord(AuthenticatedUser user, NodeFormDefinition definition, NodeFormSaveRequest request) {
        SealRecord row = requireRuntime(request).sealRecord();
        applyRuntime(definition, request, row);
        LocalDateTime now = LocalDateTime.now();
        row.setSealSubject(defaultText(row.getSealSubject(), definition.title()));
        row.setLeaderSigned(Boolean.TRUE.equals(row.getLeaderSigned()));
        row.setSchoolSealed(Boolean.TRUE.equals(row.getSchoolSealed()));
        row.setExternalSealed(Boolean.TRUE.equals(row.getExternalSealed()));
        row.setSealStatus(defaultText(row.getSealStatus(), "PENDING"));
        row.setHandledBy(user.userId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        runtimeDao.insertSealRecord(row);
        return row.getSealRecordId();
    }

    private Long insertSubmissionRecord(AuthenticatedUser user, NodeFormDefinition definition,
            NodeFormSaveRequest request) {
        SubmissionRecord row = requireRuntime(request).submissionRecord();
        applyRuntime(definition, request, row);
        LocalDateTime now = LocalDateTime.now();
        row.setSubmissionType(defaultText(row.getSubmissionType(), definition.formCode()));
        row.setSubmittedBy(user.userId());
        row.setSubmittedAt(row.getSubmittedAt() == null ? now : row.getSubmittedAt());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        runtimeDao.insertSubmissionRecord(row);
        return row.getSubmissionId();
    }

    private Long insertArchiveRecord(AuthenticatedUser user, NodeFormDefinition definition,
            NodeFormSaveRequest request) {
        ArchiveRecord row = requireRuntime(request).archiveRecord();
        applyRuntime(definition, request, row);
        LocalDateTime now = LocalDateTime.now();
        row.setArchiveType(defaultText(row.getArchiveType(), definition.formCode()));
        row.setArchiveStatus(defaultText(row.getArchiveStatus(), "ARCHIVED"));
        row.setArchivedBy(user.userId());
        row.setArchivedAt(row.getArchivedAt() == null ? now : row.getArchivedAt());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        runtimeDao.insertArchiveRecord(row);
        return row.getArchiveId();
    }

    private Long insertPublicity(AuthenticatedUser user, NodeFormSaveRequest request) {
        ProjectApplicationPublicity row = requireProjectRecord(request).publicity();
        Long projectId = requireProjectId(request);
        ProjectApplication application = applicationDao.selectByProjectId(projectId);
        LocalDateTime now = LocalDateTime.now();
        row.setProjectId(projectId);
        row.setApplicationId(application == null ? row.getApplicationId() : application.getApplicationId());
        row.setModuleInstanceId(row.getModuleInstanceId() == null ? request.moduleInstanceId() : row.getModuleInstanceId());
        row.setStateRecordId(row.getStateRecordId() == null ? request.stateRecordId() : row.getStateRecordId());
        row.setPublicityTitle(defaultText(row.getPublicityTitle(), "项目公示"));
        row.setPublicityStartDate(row.getPublicityStartDate() == null ? LocalDate.now() : row.getPublicityStartDate());
        row.setPublicityEndDate(row.getPublicityEndDate() == null ? row.getPublicityStartDate().plusDays(5) : row.getPublicityEndDate());
        row.setHasObjection(Boolean.TRUE.equals(row.getHasObjection()));
        row.setPublicityResult(defaultText(row.getPublicityResult(), "APPROVED"));
        row.setConfirmedBy(user.userId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        projectStructuredDao.insertPublicity(row);
        return row.getPublicityId();
    }

    private Long insertSurplusReturn(AuthenticatedUser user, NodeFormSaveRequest request) {
        SurplusFundsReturnRecord row = requireProjectRecord(request).surplusReturn();
        Long projectId = requireProjectId(request);
        ProjectAcceptance acceptance = acceptanceDao.selectByProjectId(projectId);
        LocalDateTime now = LocalDateTime.now();
        row.setProjectId(projectId);
        row.setAcceptanceId(acceptance == null ? row.getAcceptanceId() : acceptance.getAcceptanceId());
        row.setModuleInstanceId(row.getModuleInstanceId() == null ? request.moduleInstanceId() : row.getModuleInstanceId());
        row.setStateRecordId(row.getStateRecordId() == null ? request.stateRecordId() : row.getStateRecordId());
        row.setSurplusAmount(row.getSurplusAmount() == null ? BigDecimal.ZERO : row.getSurplusAmount());
        row.setReturnRequired(row.getReturnRequired() == null ? Boolean.TRUE : row.getReturnRequired());
        row.setReturnStatus(defaultText(row.getReturnStatus(), "PENDING"));
        row.setFinanceOperatorId(user.userId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        projectStructuredDao.insertSurplusReturn(row);
        return row.getReturnId();
    }

    private Long upsertExpert(AuthenticatedUser user, NodeFormSaveRequest request) {
        var expert = request.expertReview();
        if (expert == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expert review data is required");
        }
        if (expert.createBatch() != null) {
            return expertReviewService.create(user, expert.createBatch()).batch().getBatchId();
        }
        if (expert.batchId() != null && expert.assignExpert() != null) {
            expertReviewService.assign(user, expert.batchId(), expert.assignExpert());
            return expert.batchId();
        }
        if (expert.assignmentId() != null && expert.submitScore() != null) {
            return expertReviewService.submit(user, expert.assignmentId(), expert.submitScore()).batch().getBatchId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported expert operation");
    }

    private NodeFormSaveRequest withRuntimeContext(NodeFormSaveRequest request, Long projectId, Long moduleInstanceId,
            Long stateRecordId) {
        NodeFormSaveRequest source = request == null
                ? new NodeFormSaveRequest(projectId, moduleInstanceId, stateRecordId, null, null, null, null, null,
                        null, null, null)
                : request;
        return new NodeFormSaveRequest(
                projectId,
                moduleInstanceId,
                stateRecordId,
                source.applicationDraft(),
                source.contractDraft(),
                source.acceptanceDraft(),
                source.notice(),
                source.runtimeRecord(),
                source.projectRecord(),
                withExpertContext(source.expertReview(), moduleInstanceId),
                source.materialVersionIds());
    }

    private NodeFormExpertRequest withExpertContext(NodeFormExpertRequest expert, Long moduleInstanceId) {
        if (expert == null || expert.createBatch() == null || expert.createBatch().moduleInstanceId() != null) {
            return expert;
        }
        CreateExpertReviewBatchRequest createBatch = expert.createBatch();
        return new NodeFormExpertRequest(
                expert.batchId(),
                expert.assignmentId(),
                new CreateExpertReviewBatchRequest(moduleInstanceId, createBatch.workflowNodeId(),
                        createBatch.reviewType(), createBatch.reviewTitle(), createBatch.ruleType(),
                        createBatch.minExpertCount(), createBatch.passScore(), createBatch.recommendScore(),
                        createBatch.removeHighestLowest(), createBatch.expectedExpertCount()),
                expert.assignExpert(),
                expert.submitScore());
    }

    private AcceptanceFinancialSettlement settlement(Long recordId, AuthenticatedUser user, NodeFormSaveRequest request,
            LocalDateTime now) {
        FinancialSettlementRequest source = requireProjectRecord(request).financialSettlement();
        BigDecimal received = source.receivedAmount() == null ? BigDecimal.ZERO : source.receivedAmount();
        BigDecimal spent = source.spentAmount() == null ? BigDecimal.ZERO : source.spentAmount();
        AcceptanceFinancialSettlement row = new AcceptanceFinancialSettlement();
        row.setSettlementId(recordId);
        row.setProjectId(requireProjectId(request));
        row.setAcceptanceId(source.acceptanceId());
        row.setModuleInstanceId(source.moduleInstanceId());
        row.setStateRecordId(source.stateRecordId());
        row.setApprovedAmount(source.approvedAmount());
        row.setReceivedAmount(received);
        row.setSpentAmount(spent);
        row.setSurplusAmount(received.subtract(spent));
        row.setExecutionRate(received.signum() == 0 ? BigDecimal.ZERO
                : spent.multiply(new BigDecimal("100")).divide(received, 2, RoundingMode.HALF_UP));
        row.setSettlementResult(source.settlementResult());
        row.setFinanceOperatorId(user.userId());
        row.setFinanceReviewComment(source.financeReviewComment());
        row.setSettledAt(source.settledAt() == null ? now : source.settledAt());
        row.setUpdatedAt(now);
        return row;
    }

    private void applyCheckItem(NodeFormDefinition definition, NodeFormSaveRequest request, StateRecordCheckItem row) {
        row.setModuleInstanceId(request.moduleInstanceId());
        row.setStateRecordId(request.stateRecordId());
        row.setNodeId(definition.nodeId());
        row.setStateCode(definition.stateCode());
    }

    private void applyRuntime(NodeFormDefinition definition, NodeFormSaveRequest request, ExternalResultRecord row) {
        row.setModuleInstanceId(request.moduleInstanceId());
        row.setStateRecordId(request.stateRecordId());
        row.setModuleType(definition.moduleType().name());
    }

    private void applyRuntime(NodeFormDefinition definition, NodeFormSaveRequest request, SealRecord row) {
        row.setModuleInstanceId(request.moduleInstanceId());
        row.setStateRecordId(request.stateRecordId());
        row.setModuleType(definition.moduleType().name());
    }

    private void applyRuntime(NodeFormDefinition definition, NodeFormSaveRequest request, SubmissionRecord row) {
        row.setModuleInstanceId(request.moduleInstanceId());
        row.setStateRecordId(request.stateRecordId());
        row.setModuleType(definition.moduleType().name());
    }

    private void applyRuntime(NodeFormDefinition definition, NodeFormSaveRequest request, ArchiveRecord row) {
        row.setModuleInstanceId(request.moduleInstanceId());
        row.setStateRecordId(request.stateRecordId());
        row.setModuleType(definition.moduleType().name());
    }

    private List<StateRecordCheckItem> loadCheckItems(NodeFormContext context) {
        if (context.stateRecordId() != null) {
            return runtimeDao.selectCheckItemsByStateRecordId(context.stateRecordId());
        }
        return context.moduleInstanceId() == null ? List.of()
                : runtimeDao.selectCheckItemsByModuleInstanceId(context.moduleInstanceId());
    }

    private List<ExternalResultRecord> loadExternalResults(NodeFormContext context) {
        return context.moduleInstanceId() == null ? List.of()
                : runtimeDao.selectExternalResultsByModuleInstanceId(context.moduleInstanceId());
    }

    private List<SealRecord> loadSealRecords(NodeFormContext context) {
        return context.moduleInstanceId() == null ? List.of()
                : runtimeDao.selectSealRecordsByModuleInstanceId(context.moduleInstanceId());
    }

    private List<SubmissionRecord> loadSubmissionRecords(NodeFormContext context) {
        return context.moduleInstanceId() == null ? List.of()
                : runtimeDao.selectSubmissionRecordsByModuleInstanceId(context.moduleInstanceId());
    }

    private List<ArchiveRecord> loadArchiveRecords(NodeFormContext context) {
        return context.moduleInstanceId() == null ? List.of()
                : runtimeDao.selectArchiveRecordsByModuleInstanceId(context.moduleInstanceId());
    }

    private ExpertReviewBatchDetailResponse loadExpertReview(NodeFormContext context) {
        if (context.moduleInstanceId() == null) {
            return null;
        }
        return expertReviewBatchDao.selectByModuleInstanceId(context.moduleInstanceId()).stream()
                .reduce((first, second) -> second)
                .map(batch -> expertReviewService.detail(batch.getBatchId()))
                .orElse(null);
    }

    private NodeFormContext context(NodeFormSaveRequest request) {
        return new NodeFormContext(request.projectId(), request.moduleInstanceId(), request.stateRecordId());
    }

    private void validateRequiredContext(AuthenticatedUser user, NodeFormDefinition definition,
            NodeFormSaveRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        switch (definition.dataKind()) {
            case NOTICE -> requireProjectAccessIfPresent(user, request.projectId());
            case CHECK_ITEM, EXTERNAL_RESULT, SEAL, SUBMISSION, ARCHIVE -> {
                requireProjectAccess(user, request);
                if (request.moduleInstanceId() == null || request.stateRecordId() == null) {
                    throw missingContext(definition, "moduleInstanceId and stateRecordId");
                }
            }
            case PUBLICITY, FINANCIAL_SETTLEMENT, SURPLUS_RETURN -> {
                requireProjectAccess(user, request);
                if (request.moduleInstanceId() == null) {
                    throw missingContext(definition, "moduleInstanceId");
                }
            }
            case ACHIEVEMENT -> requireProjectAccess(user, request);
            case EXPERT_REVIEW -> {
                requireProjectAccessIfPresent(user, request.projectId());
                var expert = request.expertReview();
                if (expert != null && expert.createBatch() != null && request.moduleInstanceId() == null) {
                    throw missingContext(definition, "moduleInstanceId");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Unsupported history form");
        }
    }

    private void requireProjectAccess(AuthenticatedUser user, NodeFormSaveRequest request) {
        Long projectId = requireProjectId(request);
        requireProjectAccessIfPresent(user, projectId);
    }

    private ResponseStatusException missingContext(NodeFormDefinition definition, String fields) {
        String verb = fields.contains(" and ") ? " are " : " is ";
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fields + verb + "required for " + definition.formCode());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private Long requireProjectId(NodeFormSaveRequest request) {
        if (request == null || request.projectId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required");
        }
        return request.projectId();
    }

    private NodeFormRuntimeRecordRequest requireRuntime(NodeFormSaveRequest request) {
        if (request == null || request.runtimeRecord() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Runtime record is required");
        }
        return request.runtimeRecord();
    }

    private NodeFormProjectRecordRequest requireProjectRecord(NodeFormSaveRequest request) {
        if (request == null || request.projectRecord() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project record is required");
        }
        return request.projectRecord();
    }

    private void requireProjectAccessIfPresent(AuthenticatedUser user, Long projectId) {
        if (projectId == null) {
            return;
        }
        if (projectDao.selectById(projectId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (user == null || !permissionService.canAccessProject(user.userId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
    }
}
