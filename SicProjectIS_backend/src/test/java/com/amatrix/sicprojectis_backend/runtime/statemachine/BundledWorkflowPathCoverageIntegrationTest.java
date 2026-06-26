package com.amatrix.sicprojectis_backend.runtime.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewAssignmentDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewAssignment;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import com.amatrix.sicprojectis_backend.material.MaterialService;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormProjectRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRuntimeRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;
import com.amatrix.sicprojectis_backend.project.ProjectApplicationStartService;
import com.amatrix.sicprojectis_backend.project.ProjectGrantRoleCodes;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.dto.StartProjectApplicationRequest;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.RuntimeViewResponse;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StartModuleInstanceRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.FinancialSettlementRequest;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;
import com.amatrix.sicprojectis_backend.workflow.FlowableBpmnDefinitionParser;
import com.amatrix.sicprojectis_backend.workflow.WorkflowAssetService;
import com.amatrix.sicprojectis_backend.workflow.WorkflowDefinitionService;
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BundledWorkflowPathCoverageIntegrationTest {
    private static final String APPLICATION_ASSET = "项目申请_辅助标签版.bpmn";
    private static final String CONTRACT_ASSET = "纵向项目合同_辅助标签版.bpmn";
    private static final String ACCEPTANCE_ASSET = "项目结题_辅助标签版.bpmn";

    @Autowired
    private WorkflowDefinitionService workflowDefinitionService;
    @Autowired
    private StateMachineRuntimeService runtimeService;
    @Autowired
    private ProjectApplicationStartService applicationStartService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private ProjectDao projectDao;
    @Autowired
    private ProjectApplicationDao applicationDao;
    @Autowired
    private ProjectAcceptanceDao acceptanceDao;
    @Autowired
    private ProjectStructuredDataDao projectStructuredDao;
    @Autowired
    private ProjectRoleGrantDao projectRoleGrantDao;
    @Autowired
    private ProjectModuleInstanceDao moduleDao;
    @Autowired
    private ModuleStateRecordDao stateRecordDao;
    @Autowired
    private ExpertReviewBatchDao expertReviewBatchDao;
    @Autowired
    private ExpertReviewAssignmentDao expertReviewAssignmentDao;

    private final AuthenticatedUser leader = new AuthenticatedUser(1L, "alice", List.of("PROJECT_LEADER"));
    private final AuthenticatedUser expert = new AuthenticatedUser(2L, "bob", List.of("EXPERT"));
    private final AuthenticatedUser scienceAdmin = new AuthenticatedUser(3L, "carol", List.of("SCIENCE_ADMIN"));
    private final AuthenticatedUser deptAdmin = new AuthenticatedUser(4L, "diana", List.of("DEPT_ADMIN"));
    private final AuthenticatedUser financeAdmin = new AuthenticatedUser(5L, "eve", List.of("FINANCE_ADMIN"));

    @TestConfiguration
    static class SourceWorkflowAssetConfig {
        @Bean
        @Primary
        WorkflowAssetService sourceWorkflowAssetService(FlowableBpmnDefinitionParser parser) {
            return new WorkflowAssetService(parser) {
                @Override
                public String readAssetXml(String assetName) {
                    try {
                        Path assetPath = Path.of("src", "main", "resources", "workflows",
                                "bpmn_auxiliary_tags_augmented", assetName);
                        return Files.readString(assetPath, StandardCharsets.UTF_8);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Unable to read source BPMN asset: " + assetName, ex);
                    }
                }
            };
        }
    }

    @BeforeEach
    void publishBundledWorkflows() {
        workflowDefinitionService.publishAsset(APPLICATION_ASSET);
        workflowDefinitionService.publishAsset(CONTRACT_ASSET);
        workflowDefinitionService.publishAsset(ACCEPTANCE_ASSET);
    }

    @Test
    void applicationBundledWorkflowShouldCoverLimitedAndNonLimitedHappyPaths() {
        PathRun nonLimited = startApplication(false);
        transition(nonLimited, leader, "USER_CONFIRMED_SUBMIT", "SUBMITTED", "APPLICATION_DEPT_REVIEWING",
                "DeptReviewTask", applicationDraft(false));
        transition(nonLimited, deptAdmin, "DEPT_REVIEW_FINISHED", "APPROVED",
                "APPLICATION_SCIENCE_INITIAL_REVIEWING", "ScienceOfficeInitialReviewTask", check(true));
        transition(nonLimited, scienceAdmin, "SCIENCE_INITIAL_REVIEW_FINISHED", "APPROVED",
                "APPLICATION_SCIENCE_EXPERT_ASSIGNING", "ScienceExpertAssignTask", check(true));
        createExpertAssignmentBatch(nonLimited, "APPLICATION_SCIENCE_EXPERT");
        transition(nonLimited, scienceAdmin, "SCIENCE_EXPERT_ASSIGNED", "ASSIGNED",
                "APPLICATION_SCIENCE_EXPERT_REVIEWING", "ScienceExpertReviewTask", null);
        grantExpert(nonLimited.projectId, "APPLICATION", "ScienceExpertReviewTask", currentRound(nonLimited.moduleId));
        transition(nonLimited, expert, "SCIENCE_EXPERT_REVIEW_SUBMITTED", "REVIEW_SUBMITTED",
                "APPLICATION_SCIENCE_EXPERT_SUMMARIZING", "ScienceExpertSummaryTask", null);
        createExpertBatchResult(nonLimited.moduleId, "APPLICATION_SCIENCE_EXPERT", "PASSED");
        transition(nonLimited, scienceAdmin, "SCIENCE_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED",
                "APPLICATION_PUBLICITY", "ScienceOfficePublicityTask", null);
        transition(nonLimited, scienceAdmin, "PUBLICITY_FINISHED", "APPROVED",
                "APPLICATION_SCIENCE_SUBMITTING", "ScienceOfficeSubmitTask", publicity("APPROVED"));
        transition(nonLimited, scienceAdmin, "SCIENCE_SUBMIT_TO_AUTHORITY", "SUBMITTED",
                "APPLICATION_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        transition(nonLimited, scienceAdmin, "AUTHORITY_REVIEW_FINISHED", "APPROVED",
                "APPLICATION_SIGN_SEALING", "SignAndSealTask", externalResult("APPROVED"));
        transition(nonLimited, leader, "SIGN_AND_SEAL_COMPLETED", "SIGNED",
                "APPLICATION_FINAL_MATERIAL_SUBMITTING", "SubmitFinalMaterialsTask", seal());
        transition(nonLimited, scienceAdmin, "FINAL_MATERIALS_SUBMITTED", "PROCESS_COMPLETED",
                "APPLICATION_APPROVED", "ApprovedEndEvent", submission());
        assertFinished(nonLimited.moduleId);

        PathRun limited = startApplication(true);
        transition(limited, leader, "USER_CONFIRMED_SUBMIT", "SUBMITTED", "APPLICATION_DEPT_REVIEWING",
                "DeptReviewTask", applicationDraft(true));
        transition(limited, deptAdmin, "DEPT_REVIEW_FINISHED", "APPROVED",
                "APPLICATION_DEPT_EXPERT_ASSIGNING", "DeptExpertAssignTask", check(true));
        createExpertAssignmentBatch(limited, "APPLICATION_DEPT_EXPERT");
        transition(limited, deptAdmin, "DEPT_EXPERT_ASSIGNED", "ASSIGNED",
                "APPLICATION_DEPT_EXPERT_REVIEWING", "DeptExpertReviewTask", null);
        grantExpert(limited.projectId, "APPLICATION", "DeptExpertReviewTask", currentRound(limited.moduleId));
        transition(limited, expert, "DEPT_EXPERT_REVIEW_SUBMITTED", "REVIEW_SUBMITTED",
                "APPLICATION_DEPT_EXPERT_SUMMARIZING", "DeptExpertSummaryTask", null);
        createExpertBatchResult(limited.moduleId, "APPLICATION_DEPT_EXPERT", "PASSED");
        transition(limited, deptAdmin, "DEPT_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED",
                "APPLICATION_SCIENCE_INITIAL_REVIEWING", "ScienceOfficeInitialReviewTask", null);

        assertThat(nonLimited.events).contains("NON_LIMITED_PROJECT_SELECTED", "SCIENCE_EXPERT_APPROVE",
                "FINAL_MATERIALS_SUBMITTED");
        assertThat(limited.events).contains("LIMITED_PROJECT_SELECTED", "DEPT_EXPERT_APPROVE");
    }
    @Test
    void applicationBundledWorkflowShouldCoverReturnBranches() {
        assertApplicationReturn("DEPT_REVIEW_FINISHED", deptAdmin, check(false));

        PathRun deptExpert = driveApplicationToDeptExpertSummary();
        createExpertBatchResult(deptExpert.moduleId, "APPLICATION_DEPT_EXPERT", "REJECTED");
        assertReturn(transition(deptExpert, deptAdmin, "DEPT_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED",
                "APPLICATION_DRAFT", "SubmitApplicationTask", null), deptExpert.moduleId, "APPLICATION_DRAFT");

        PathRun scienceInitial = driveApplicationToScienceInitial(false);
        assertReturn(transition(scienceInitial, scienceAdmin, "SCIENCE_INITIAL_REVIEW_FINISHED", "RETURNED",
                "APPLICATION_DRAFT", "SubmitApplicationTask", check(false)), scienceInitial.moduleId,
                "APPLICATION_DRAFT");

        PathRun scienceExpert = driveApplicationToScienceExpertSummary(false);
        createExpertBatchResult(scienceExpert.moduleId, "APPLICATION_SCIENCE_EXPERT", "REJECTED");
        assertReturn(transition(scienceExpert, scienceAdmin, "SCIENCE_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED",
                "APPLICATION_DRAFT", "SubmitApplicationTask", null), scienceExpert.moduleId, "APPLICATION_DRAFT");

        PathRun publicity = driveApplicationToPublicity(false);
        assertReturn(transition(publicity, scienceAdmin, "PUBLICITY_FINISHED", "RETURNED",
                "APPLICATION_DRAFT", "SubmitApplicationTask", publicity("RETURNED")), publicity.moduleId,
                "APPLICATION_DRAFT");

        PathRun authority = driveApplicationToAuthority(false);
        assertReturn(transition(authority, scienceAdmin, "AUTHORITY_REVIEW_FINISHED", "RETURNED",
                "APPLICATION_DRAFT", "SubmitApplicationTask", externalResult("REJECTED")), authority.moduleId,
                "APPLICATION_DRAFT");
    }

    @Test
    void contractBundledWorkflowShouldCoverHappyPathAndReturnBranches() {
        PathRun happy = startModule("CONTRACT");
        transition(happy, scienceAdmin, "PROJECT_APPROVAL_REGISTERED", "APPROVED", "CONTRACT_DRAFT",
                "FillContractTask", externalResult("APPROVED"));
        transition(happy, leader, "CONTRACT_CONFIRMED_SUBMIT", "SUBMITTED", "CONTRACT_DEPT_REVIEWING",
                "DeptReviewTask", contractDraft());
        transition(happy, deptAdmin, "DEPT_CONTRACT_REVIEW_FINISHED", "APPROVED",
                "CONTRACT_SCIENCE_REVIEWING", "ScienceOfficeReviewTask", check(true));
        transition(happy, scienceAdmin, "SCIENCE_CONTRACT_REVIEW_FINISHED", "APPROVED",
                "CONTRACT_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        transition(happy, scienceAdmin, "AUTHORITY_CONTRACT_REVIEW_FINISHED", "APPROVED",
                "CONTRACT_PDF_PRINTING", "PrintPdfContractTask", externalResult("APPROVED"));
        transition(happy, leader, "CONTRACT_PDF_PRINTED", "PDF_PRINTED", "CONTRACT_LEADER_SIGNING",
                "LeaderSignTask", null);
        transition(happy, leader, "LEADER_SIGN_COMPLETED", "SIGNED", "CONTRACT_SCHOOL_SEALING",
                "SchoolSealTask", seal());
        transition(happy, scienceAdmin, "SCHOOL_SEAL_COMPLETED", "SEALED", "CONTRACT_AUTHORITY_SEALING",
                "AuthoritySealTask", seal());
        transition(happy, scienceAdmin, "AUTHORITY_SEAL_COMPLETED", "SEALED", "CONTRACT_ARCHIVING",
                "ArchiveContractTask", externalResult("APPROVED"));
        transition(happy, scienceAdmin, "CONTRACT_ARCHIVED", "PROCESS_COMPLETED", "CONTRACT_APPROVED",
                "ApprovedEndEvent", archive());
        assertFinished(happy.moduleId);
        assertThat(happy.events).contains("DEPT_CONTRACT_APPROVE", "SCIENCE_CONTRACT_APPROVE",
                "AUTHORITY_CONTRACT_APPROVE", "CONTRACT_ARCHIVED");

        assertContractReturn("DEPT_CONTRACT_REVIEW_FINISHED", deptAdmin, check(false));
        PathRun scienceReturn = driveContractToScienceReview();
        assertReturn(transition(scienceReturn, scienceAdmin, "SCIENCE_CONTRACT_REVIEW_FINISHED", "RETURNED",
                "CONTRACT_DRAFT", "FillContractTask", check(false)), scienceReturn.moduleId, "CONTRACT_DRAFT");
        PathRun authorityReturn = driveContractToAuthorityReview();
        assertReturn(transition(authorityReturn, scienceAdmin, "AUTHORITY_CONTRACT_REVIEW_FINISHED", "RETURNED",
                "CONTRACT_DRAFT", "FillContractTask", externalResult("REJECTED")), authorityReturn.moduleId,
                "CONTRACT_DRAFT");
    }
    @Test
    void acceptanceBundledWorkflowShouldCoverHappyRejectAndReturnBranches() {
        PathRun nonSchool = startAcceptance(false);
        driveAcceptanceToScienceGateway(nonSchool);
        transition(nonSchool, scienceAdmin, "SCIENCE_ACCEPTANCE_REVIEW_FINISHED", "APPROVED",
                "ACCEPTANCE_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        transition(nonSchool, scienceAdmin, "AUTHORITY_ACCEPTANCE_REVIEW_FINISHED", "APPROVED",
                "ACCEPTANCE_SIGN_SEALING", "SignSealAcceptanceTask", externalResult("APPROVED"));
        transition(nonSchool, leader, "ACCEPTANCE_SIGN_SEAL_COMPLETED", "SIGNED",
                "ACCEPTANCE_FINAL_MATERIAL_SUBMITTING", "SubmitFinalMaterialsTask", seal());
        transition(nonSchool, scienceAdmin, "ACCEPTANCE_FINAL_MATERIALS_SUBMITTED", "SUBMITTED",
                "ACCEPTANCE_EXPERT_ASSIGNING", "ExpertAssignTask", submission());
        driveAcceptanceExpertToSummary(nonSchool);
        createExpertBatchResult(nonSchool.moduleId, "ACCEPTANCE_EXPERT", "PASSED");
        transition(nonSchool, scienceAdmin, "EXPERT_ACCEPTANCE_REVIEW_FINISHED", "REVIEW_FINISHED",
                "ACCEPTANCE_CERTIFICATE_ISSUING", "IssueCertificateTask", null);
        transition(nonSchool, scienceAdmin, "ACCEPTANCE_CERTIFICATE_ISSUED", "PROCESS_COMPLETED",
                "ACCEPTANCE_ACCEPTED", "AcceptedEndEvent", null);
        assertFinished(nonSchool.moduleId);
        assertThat(nonSchool.events).contains("NON_SCHOOL_LEVEL_ACCEPTANCE_SELECTED",
                "AUTHORITY_ACCEPTANCE_APPROVE", "EXPERT_ACCEPTANCE_APPROVE");

        PathRun schoolReject = startAcceptance(true);
        driveAcceptanceToScienceGateway(schoolReject);
        transition(schoolReject, scienceAdmin, "SCIENCE_ACCEPTANCE_REVIEW_FINISHED", "APPROVED",
                "ACCEPTANCE_EXPERT_ASSIGNING", "ExpertAssignTask", check(true));
        driveAcceptanceExpertToSummary(schoolReject);
        createExpertBatchResult(schoolReject.moduleId, "ACCEPTANCE_EXPERT", "REJECTED");
        transition(schoolReject, scienceAdmin, "EXPERT_ACCEPTANCE_REVIEW_FINISHED", "REVIEW_FINISHED",
                "ACCEPTANCE_FAIL_FILE_ISSUING", "IssueFailFileTask", null);
        transition(schoolReject, scienceAdmin, "ACCEPTANCE_FAIL_FILE_ISSUED", "FAIL_FILE_ISSUED",
                "ACCEPTANCE_SURPLUS_FUNDS_RETURNING", "ReturnSurplusFundsTask", null);
        transition(schoolReject, financeAdmin, "SURPLUS_FUNDS_RETURNED", "PROCESS_REJECTED",
                "ACCEPTANCE_REJECTED", "RejectedEndEvent", surplusReturn());
        assertFinished(schoolReject.moduleId);
        assertThat(schoolReject.events).contains("SCHOOL_LEVEL_ACCEPTANCE_SELECTED", "EXPERT_ACCEPTANCE_REJECT");

        assertAcceptanceReturn("DEPT_ACCEPTANCE_REVIEW_FINISHED", deptAdmin, check(false));
        PathRun scienceReturn = driveAcceptanceToScienceReview(false);
        assertReturn(transition(scienceReturn, scienceAdmin, "SCIENCE_ACCEPTANCE_REVIEW_FINISHED", "RETURNED",
                "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", check(false)), scienceReturn.moduleId,
                "ACCEPTANCE_MATERIAL_DRAFT");
        PathRun authorityReturn = startAcceptance(false);
        driveAcceptanceToScienceGateway(authorityReturn);
        transition(authorityReturn, scienceAdmin, "SCIENCE_ACCEPTANCE_REVIEW_FINISHED", "APPROVED",
                "ACCEPTANCE_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        assertReturn(transition(authorityReturn, scienceAdmin, "AUTHORITY_ACCEPTANCE_REVIEW_FINISHED", "RETURNED",
                "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", externalResult("REJECTED")),
                authorityReturn.moduleId, "ACCEPTANCE_MATERIAL_DRAFT");
    }

    @Test
    void bundledWorkflowRuntimeShouldRejectStaleUnauthorizedAndMissingMaterials() {
        PathRun run = startModule("CONTRACT");
        assertThatThrownBy(() -> runtimeService.transition(scienceAdmin, run.moduleId,
                new StateTransitionRequest("PROJECT_APPROVAL_REGISTERED", 0, "APPROVED", "stale", List.of(),
                        null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        assertThatThrownBy(() -> runtimeService.transition(leader, run.moduleId,
                new StateTransitionRequest("PROJECT_APPROVAL_REGISTERED", 1, "APPROVED", "wrong user",
                        List.of(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        assertThatThrownBy(() -> runtimeService.transition(scienceAdmin, run.moduleId,
                new StateTransitionRequest("PROJECT_APPROVAL_REGISTERED", 1, "APPROVED", "missing material",
                        List.of(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Material is required");

        PathRun expertAssign = driveApplicationToScienceInitial(false);
        transition(expertAssign, scienceAdmin, "SCIENCE_INITIAL_REVIEW_FINISHED", "APPROVED",
                "APPLICATION_SCIENCE_EXPERT_ASSIGNING", "ScienceExpertAssignTask", check(true));
        assertThatThrownBy(() -> runtimeService.transition(scienceAdmin, expertAssign.moduleId,
                new StateTransitionRequest("SCIENCE_EXPERT_ASSIGNED", currentSeq(expertAssign.moduleId), "ASSIGNED",
                        "no experts", List.of(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("At least one expert must be assigned");
    }
    private void assertApplicationReturn(String eventType, AuthenticatedUser user, NodeFormSaveRequest form) {
        PathRun run = startApplication(false);
        transition(run, leader, "USER_CONFIRMED_SUBMIT", "SUBMITTED", "APPLICATION_DEPT_REVIEWING", "DeptReviewTask", applicationDraft(false));
        assertReturn(transition(run, user, eventType, "RETURNED", "APPLICATION_DRAFT", "SubmitApplicationTask", form), run.moduleId, "APPLICATION_DRAFT");
    }

    private void assertContractReturn(String eventType, AuthenticatedUser user, NodeFormSaveRequest form) {
        PathRun run = startModule("CONTRACT");
        transition(run, scienceAdmin, "PROJECT_APPROVAL_REGISTERED", "APPROVED", "CONTRACT_DRAFT", "FillContractTask", externalResult("APPROVED"));
        transition(run, leader, "CONTRACT_CONFIRMED_SUBMIT", "SUBMITTED", "CONTRACT_DEPT_REVIEWING", "DeptReviewTask", contractDraft());
        assertReturn(transition(run, user, eventType, "RETURNED", "CONTRACT_DRAFT", "FillContractTask", form), run.moduleId, "CONTRACT_DRAFT");
    }

    private void assertAcceptanceReturn(String eventType, AuthenticatedUser user, NodeFormSaveRequest form) {
        PathRun run = startAcceptance(false);
        transition(run, scienceAdmin, "ACCEPTANCE_NOTICE_PUBLISHED", "NOTICE_PUBLISHED", "ACCEPTANCE_DEPT_NOTIFYING", "DeptNotifyLeaderTask", null);
        transition(run, deptAdmin, "DEPT_LEADER_NOTIFIED", "NOTIFIED", "ACCEPTANCE_FINANCIAL_SETTLEMENT", "FinancialSettlementTask", check(true));
        transition(run, financeAdmin, "FINANCIAL_SETTLEMENT_COMPLETED", "SETTLED", "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", settlement(run.moduleId));
        transition(run, leader, "ACCEPTANCE_CONFIRMED_SUBMIT", "SUBMITTED", "ACCEPTANCE_DEPT_REVIEWING", "DeptReviewTask", acceptanceDraft(false));
        assertReturn(transition(run, user, eventType, "RETURNED", "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", form), run.moduleId, "ACCEPTANCE_MATERIAL_DRAFT");
    }

    private PathRun driveApplicationToDeptExpertSummary() {
        PathRun run = startApplication(true);
        transition(run, leader, "USER_CONFIRMED_SUBMIT", "SUBMITTED", "APPLICATION_DEPT_REVIEWING", "DeptReviewTask", applicationDraft(true));
        transition(run, deptAdmin, "DEPT_REVIEW_FINISHED", "APPROVED", "APPLICATION_DEPT_EXPERT_ASSIGNING", "DeptExpertAssignTask", check(true));
        createExpertAssignmentBatch(run, "APPLICATION_DEPT_EXPERT");
        transition(run, deptAdmin, "DEPT_EXPERT_ASSIGNED", "ASSIGNED", "APPLICATION_DEPT_EXPERT_REVIEWING", "DeptExpertReviewTask", null);
        grantExpert(run.projectId, "APPLICATION", "DeptExpertReviewTask", currentRound(run.moduleId));
        transition(run, expert, "DEPT_EXPERT_REVIEW_SUBMITTED", "REVIEW_SUBMITTED", "APPLICATION_DEPT_EXPERT_SUMMARIZING", "DeptExpertSummaryTask", null);
        return run;
    }

    private PathRun driveApplicationToScienceInitial(boolean limited) {
        if (limited) {
            PathRun run = driveApplicationToDeptExpertSummary();
            createExpertBatchResult(run.moduleId, "APPLICATION_DEPT_EXPERT", "PASSED");
            transition(run, deptAdmin, "DEPT_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED", "APPLICATION_SCIENCE_INITIAL_REVIEWING", "ScienceOfficeInitialReviewTask", null);
            return run;
        }
        PathRun run = startApplication(false);
        transition(run, leader, "USER_CONFIRMED_SUBMIT", "SUBMITTED", "APPLICATION_DEPT_REVIEWING", "DeptReviewTask", applicationDraft(false));
        transition(run, deptAdmin, "DEPT_REVIEW_FINISHED", "APPROVED", "APPLICATION_SCIENCE_INITIAL_REVIEWING", "ScienceOfficeInitialReviewTask", check(true));
        return run;
    }

    private PathRun driveApplicationToScienceExpertSummary(boolean limited) {
        PathRun run = driveApplicationToScienceInitial(limited);
        transition(run, scienceAdmin, "SCIENCE_INITIAL_REVIEW_FINISHED", "APPROVED", "APPLICATION_SCIENCE_EXPERT_ASSIGNING", "ScienceExpertAssignTask", check(true));
        createExpertAssignmentBatch(run, "APPLICATION_SCIENCE_EXPERT");
        transition(run, scienceAdmin, "SCIENCE_EXPERT_ASSIGNED", "ASSIGNED", "APPLICATION_SCIENCE_EXPERT_REVIEWING", "ScienceExpertReviewTask", null);
        grantExpert(run.projectId, "APPLICATION", "ScienceExpertReviewTask", currentRound(run.moduleId));
        transition(run, expert, "SCIENCE_EXPERT_REVIEW_SUBMITTED", "REVIEW_SUBMITTED", "APPLICATION_SCIENCE_EXPERT_SUMMARIZING", "ScienceExpertSummaryTask", null);
        return run;
    }

    private PathRun driveApplicationToPublicity(boolean limited) {
        PathRun run = driveApplicationToScienceExpertSummary(limited);
        createExpertBatchResult(run.moduleId, "APPLICATION_SCIENCE_EXPERT", "PASSED");
        transition(run, scienceAdmin, "SCIENCE_EXPERT_REVIEW_FINISHED", "REVIEW_FINISHED", "APPLICATION_PUBLICITY", "ScienceOfficePublicityTask", null);
        return run;
    }

    private PathRun driveApplicationToAuthority(boolean limited) {
        PathRun run = driveApplicationToPublicity(limited);
        transition(run, scienceAdmin, "PUBLICITY_FINISHED", "APPROVED", "APPLICATION_SCIENCE_SUBMITTING", "ScienceOfficeSubmitTask", publicity("APPROVED"));
        transition(run, scienceAdmin, "SCIENCE_SUBMIT_TO_AUTHORITY", "SUBMITTED", "APPLICATION_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        return run;
    }

    private PathRun driveContractToScienceReview() {
        PathRun run = startModule("CONTRACT");
        transition(run, scienceAdmin, "PROJECT_APPROVAL_REGISTERED", "APPROVED", "CONTRACT_DRAFT", "FillContractTask", externalResult("APPROVED"));
        transition(run, leader, "CONTRACT_CONFIRMED_SUBMIT", "SUBMITTED", "CONTRACT_DEPT_REVIEWING", "DeptReviewTask", contractDraft());
        transition(run, deptAdmin, "DEPT_CONTRACT_REVIEW_FINISHED", "APPROVED", "CONTRACT_SCIENCE_REVIEWING", "ScienceOfficeReviewTask", check(true));
        return run;
    }

    private PathRun driveContractToAuthorityReview() {
        PathRun run = driveContractToScienceReview();
        transition(run, scienceAdmin, "SCIENCE_CONTRACT_REVIEW_FINISHED", "APPROVED", "CONTRACT_AUTHORITY_REVIEWING", "AuthorityReviewTask", check(true));
        return run;
    }
    private PathRun startAcceptance(boolean schoolLevel) {
        PathRun run = startModule("ACCEPTANCE");
        ensureAcceptance(run.projectId, run.moduleId, schoolLevel);
        grantFinance(run.projectId);
        return run;
    }

    private PathRun driveAcceptanceToScienceReview(boolean schoolLevel) {
        PathRun run = startAcceptance(schoolLevel);
        transition(run, scienceAdmin, "ACCEPTANCE_NOTICE_PUBLISHED", "NOTICE_PUBLISHED", "ACCEPTANCE_DEPT_NOTIFYING", "DeptNotifyLeaderTask", null);
        transition(run, deptAdmin, "DEPT_LEADER_NOTIFIED", "NOTIFIED", "ACCEPTANCE_FINANCIAL_SETTLEMENT", "FinancialSettlementTask", check(true));
        transition(run, financeAdmin, "FINANCIAL_SETTLEMENT_COMPLETED", "SETTLED", "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", settlement(run.moduleId));
        transition(run, leader, "ACCEPTANCE_CONFIRMED_SUBMIT", "SUBMITTED", "ACCEPTANCE_DEPT_REVIEWING", "DeptReviewTask", acceptanceDraft(schoolLevel));
        transition(run, deptAdmin, "DEPT_ACCEPTANCE_REVIEW_FINISHED", "APPROVED", "ACCEPTANCE_SCIENCE_REVIEWING", "ScienceOfficeReviewTask", check(true));
        return run;
    }

    private void driveAcceptanceToScienceGateway(PathRun run) {
        transition(run, scienceAdmin, "ACCEPTANCE_NOTICE_PUBLISHED", "NOTICE_PUBLISHED", "ACCEPTANCE_DEPT_NOTIFYING", "DeptNotifyLeaderTask", null);
        transition(run, deptAdmin, "DEPT_LEADER_NOTIFIED", "NOTIFIED", "ACCEPTANCE_FINANCIAL_SETTLEMENT", "FinancialSettlementTask", check(true));
        transition(run, financeAdmin, "FINANCIAL_SETTLEMENT_COMPLETED", "SETTLED", "ACCEPTANCE_MATERIAL_DRAFT", "SubmitAcceptanceMaterialsTask", settlement(run.moduleId));
        transition(run, leader, "ACCEPTANCE_CONFIRMED_SUBMIT", "SUBMITTED", "ACCEPTANCE_DEPT_REVIEWING", "DeptReviewTask", acceptanceDraft(isSchoolLevel(run.projectId)));
        transition(run, deptAdmin, "DEPT_ACCEPTANCE_REVIEW_FINISHED", "APPROVED", "ACCEPTANCE_SCIENCE_REVIEWING", "ScienceOfficeReviewTask", check(true));
    }

    private void driveAcceptanceExpertToSummary(PathRun run) {
        createExpertAssignmentBatch(run, "ACCEPTANCE_EXPERT");
        transition(run, scienceAdmin, "EXPERT_ACCEPTANCE_ASSIGNED", "ASSIGNED", "ACCEPTANCE_EXPERT_REVIEWING", "ExpertReviewTask", null);
        grantExpert(run.projectId, "ACCEPTANCE", "ExpertReviewTask", currentRound(run.moduleId));
        transition(run, expert, "EXPERT_ACCEPTANCE_REVIEW_SUBMITTED", "REVIEW_SUBMITTED", "ACCEPTANCE_EXPERT_SUMMARIZING", "ExpertSummaryTask", null);
    }

    private PathRun startApplication(boolean limited) {
        var response = applicationStartService.start(leader,
                new StartProjectApplicationRequest(null, "Whitebox Application " + System.nanoTime(), "RESEARCH", "NATIONAL",
                        new BigDecimal("100000"), LocalDate.now(), LocalDate.now().plusMonths(12),
                        "Whitebox Application", limited, "summary"));
        setApplicationLimited(response.projectId(), limited);
        return new PathRun(response.projectId(), response.moduleInstanceId());
    }

    private PathRun startModule(String moduleType) {
        Long projectId = createProject();
        var response = runtimeService.startModule(scienceAdmin, projectId, new StartModuleInstanceRequest(moduleType));
        return new PathRun(projectId, response.stateRecord().getModuleInstanceId());
    }

    private Long createProject() {
        Project project = new Project();
        project.setProjectCode("WB-" + System.nanoTime());
        project.setProjectName("Whitebox Project");
        project.setLeaderUserId(leader.userId());
        project.setDeptId(1L);
        project.setProjectType("RESEARCH");
        project.setProjectLevel("NATIONAL");
        project.setApprovedAmount(new BigDecimal("100000"));
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusMonths(12));
        project.setLifecycleStage("TESTING");
        projectDao.insert(project);
        return project.getProjectId();
    }

    private StateTransitionResponse transition(PathRun run, AuthenticatedUser user, String eventType, String result,
            String expectedState, String expectedNode, NodeFormSaveRequest formData) {
        RuntimeViewResponse before = runtimeService.runtimeView(user, run.moduleId);
        List<Long> materialIds = uploadRequiredMaterials(user, before.context().getProjectId(), before);
        String formCode = formData == null ? null : before.nodeForms().stream().findFirst().map(f -> f.formCode()).orElse(null);
        var response = runtimeService.transition(user, run.moduleId,
                new StateTransitionRequest(eventType, before.context().getCurrentSeq(), result, eventType,
                        materialIds, formCode, formData));
        var record = response.stateRecord();
        run.events.add(eventType);
        run.events.add(record.getEventType());
        collectTraversedGatewayEvents(run, record.getStateRecordId());
        assertThat(response.currentState()).as(eventType).isEqualTo(expectedState);
        assertThat(response.currentNodeId()).as(eventType).isEqualTo(expectedNode);
        assertThat(record.getSeq()).as(eventType).isEqualTo(before.context().getCurrentSeq() + 1);
        assertOpenTaskMatchesTarget(run.moduleId, response);
        return response;
    }

    private void collectTraversedGatewayEvents(PathRun run, Long stateRecordId) {
        var latest = stateRecordDao.selectById(stateRecordId);
        if (latest == null) return;
        String to = latest.getToNodeId();
        String from = latest.getFromNodeId();
        if ("DeptExpertAssignTask".equals(to)) run.events.add("LIMITED_PROJECT_SELECTED");
        if ("ScienceOfficeInitialReviewTask".equals(to) && "DeptReviewTask".equals(from)) run.events.add("NON_LIMITED_PROJECT_SELECTED");
        if ("ScienceOfficeInitialReviewTask".equals(to) && "DeptExpertSummaryTask".equals(from)) run.events.add("DEPT_EXPERT_APPROVE");
        if ("ScienceExpertAssignTask".equals(to)) run.events.add("SCIENCE_INITIAL_APPROVE");
        if ("ScienceOfficePublicityTask".equals(to)) run.events.add("SCIENCE_EXPERT_APPROVE");
        if ("ScienceOfficeSubmitTask".equals(to)) run.events.add("PUBLICITY_APPROVE");
        if ("SignAndSealTask".equals(to)) run.events.add("AUTHORITY_APPROVE");
        if ("ScienceOfficeReviewTask".equals(to) && "DeptReviewTask".equals(from)) run.events.add("DEPT_CONTRACT_APPROVE");
        if ("AuthorityReviewTask".equals(to) && "ScienceOfficeReviewTask".equals(from)) run.events.add("SCIENCE_CONTRACT_APPROVE");
        if ("PrintPdfContractTask".equals(to)) run.events.add("AUTHORITY_CONTRACT_APPROVE");
        if ("AuthorityReviewTask".equals(to) && "ScienceOfficeReviewTask".equals(from)) run.events.add("NON_SCHOOL_LEVEL_ACCEPTANCE_SELECTED");
        if ("ExpertAssignTask".equals(to) && "ScienceOfficeReviewTask".equals(from)) run.events.add("SCHOOL_LEVEL_ACCEPTANCE_SELECTED");
        if ("SignSealAcceptanceTask".equals(to)) run.events.add("AUTHORITY_ACCEPTANCE_APPROVE");
        if ("IssueCertificateTask".equals(to)) run.events.add("EXPERT_ACCEPTANCE_APPROVE");
        if ("IssueFailFileTask".equals(to)) run.events.add("EXPERT_ACCEPTANCE_REJECT");
        if ("APPLICATION_DRAFT".equals(latest.getToState()) || "CONTRACT_DRAFT".equals(latest.getToState())
                || "ACCEPTANCE_MATERIAL_DRAFT".equals(latest.getToState())) run.events.add("RETURN_BRANCH");
    }
    private void assertOpenTaskMatchesTarget(Long moduleId, StateTransitionResponse response) {
        var openTasks = runtimeService.runtimeView(scienceAdmin, moduleId).openTasks();
        if (response.finished()) {
            assertThat(openTasks).isEmpty();
            return;
        }
        assertThat(openTasks).hasSize(1);
        assertThat(openTasks.getFirst().getNodeId()).isEqualTo(response.currentNodeId());
        assertThat(openTasks.getFirst().getStateCode()).isEqualTo(response.currentState());
        assertThat(openTasks.getFirst().getCandidateRoleCode()).isNotBlank();
    }

    private List<Long> uploadRequiredMaterials(AuthenticatedUser user, Long projectId, RuntimeViewResponse view) {
        List<Long> ids = new ArrayList<>();
        for (var requirement : view.materialRequirements()) {
            if (Boolean.TRUE.equals(requirement.required())) {
                var uploaded = materialService.upload(user, projectId, requirement.materialTypeCode(),
                        new MockMultipartFile("file", requirement.materialTypeCode() + ".pdf", "application/pdf",
                                requirement.materialTypeCode().getBytes()));
                ids.add(uploaded.version().getMaterialVersionId());
            }
        }
        return ids;
    }

    private void assertReturn(StateTransitionResponse response, Long moduleId, String expectedState) {
        assertThat(response.currentState()).isEqualTo(expectedState);
        assertThat(response.stateRecord().getRoundNo()).isGreaterThan(1);
        assertThat(runtimeService.runtimeView(scienceAdmin, moduleId).openTasks()).hasSize(1);
    }

    private void assertFinished(Long moduleId) {
        assertThat(moduleDao.selectById(moduleId).getFinishedAt()).isNotNull();
        assertThat(runtimeService.runtimeView(scienceAdmin, moduleId).openTasks()).isEmpty();
    }

    private int currentSeq(Long moduleId) {
        return stateRecordDao.selectLatestByModuleInstanceId(moduleId).getSeq();
    }

    private int currentRound(Long moduleId) {
        return runtimeService.runtimeView(scienceAdmin, moduleId).context().getCurrentRoundNo();
    }

    private NodeFormSaveRequest applicationDraft(boolean limited) {
        ProjectApplication application = new ProjectApplication();
        application.setApplicationTitle("Application");
        application.setApplicationSummary("summary");
        application.setIsLimitedProject(limited);
        ProjectApplicationExt ext = new ProjectApplicationExt();
        ext.setIsLimitedProject(limited);
        return save(new ApplicationDraftRequest(application, ext, null));
    }

    private NodeFormSaveRequest contractDraft() {
        ProjectContract contract = new ProjectContract();
        contract.setContractCode("CT-" + System.nanoTime());
        contract.setContractName("Contract");
        contract.setContractAmount(new BigDecimal("100000"));
        contract.setContractStartDate(LocalDate.now());
        contract.setContractEndDate(LocalDate.now().plusMonths(12));
        contract.setSealStatus("PENDING");
        return save(new ContractDraftRequest(contract, null));
    }

    private NodeFormSaveRequest acceptanceDraft(boolean schoolLevel) {
        ProjectAcceptance acceptance = new ProjectAcceptance();
        acceptance.setSubmittedAt(LocalDateTime.now());
        acceptance.setConclusion("Pending");
        ProjectAcceptanceExt ext = new ProjectAcceptanceExt();
        ext.setIsSchoolLevelAcceptance(schoolLevel);
        return save(new AcceptanceDraftRequest(acceptance, ext));
    }

    private NodeFormSaveRequest check(boolean passed) {
        StateRecordCheckItem item = new StateRecordCheckItem();
        item.setItemCode(passed ? "PASS" : "REJECT");
        item.setItemName("Whitebox check");
        item.setItemType("BOOLEAN");
        item.setRequired(true);
        item.setPassed(passed);
        item.setRemark(passed ? "approved" : "returned");
        return save(new NodeFormRuntimeRecordRequest(item, null, null, null, null));
    }

    private NodeFormSaveRequest externalResult(String result) {
        ExternalResultRecord row = new ExternalResultRecord();
        row.setExternalActorCode("AUTHORITY");
        row.setExternalActorName("Authority");
        row.setExternalResult(result);
        row.setResultType("WHITEBOX_EXTERNAL_RESULT");
        row.setExternalResultDate(LocalDate.now());
        return save(new NodeFormRuntimeRecordRequest(null, row, null, null, null));
    }

    private NodeFormSaveRequest seal() {
        SealRecord row = new SealRecord();
        row.setSealSubject("Whitebox seal");
        row.setSealStatus("SEALED");
        row.setLeaderSigned(true);
        row.setSchoolSealed(true);
        row.setExternalSealed(true);
        return save(new NodeFormRuntimeRecordRequest(null, null, row, null, null));
    }

    private NodeFormSaveRequest submission() {
        SubmissionRecord row = new SubmissionRecord();
        row.setSubmissionType("WHITEBOX_SUBMISSION");
        row.setTargetActorCode("AUTHORITY");
        row.setTargetActorName("Authority");
        row.setSubmissionMethod("ONLINE");
        return save(new NodeFormRuntimeRecordRequest(null, null, null, row, null));
    }

    private NodeFormSaveRequest archive() {
        ArchiveRecord row = new ArchiveRecord();
        row.setArchiveType("CONTRACT");
        row.setArchiveStatus("ARCHIVED");
        return save(new NodeFormRuntimeRecordRequest(null, null, null, null, row));
    }

    private NodeFormSaveRequest publicity(String result) {
        ProjectApplicationPublicity row = new ProjectApplicationPublicity();
        row.setPublicityTitle("Whitebox publicity");
        row.setPublicityResult(result);
        row.setHasObjection(!"APPROVED".equals(result));
        return save(new NodeFormProjectRecordRequest(row, null, null, null));
    }

    private NodeFormSaveRequest settlement(Long moduleInstanceId) {
        return save(new NodeFormProjectRecordRequest(null,
                new FinancialSettlementRequest(null, moduleInstanceId, null, new BigDecimal("100000"), new BigDecimal("100000"),
                        new BigDecimal("90000"), "SETTLED", "ok", LocalDateTime.now()),
                null, null));
    }

    private NodeFormSaveRequest surplusReturn() {
        SurplusFundsReturnRecord row = new SurplusFundsReturnRecord();
        row.setSurplusAmount(new BigDecimal("10000"));
        row.setReturnRequired(true);
        row.setReturnStatus("RETURNED");
        return save(new NodeFormProjectRecordRequest(null, null, null, row));
    }

    private NodeFormSaveRequest save(ApplicationDraftRequest request) {
        return new NodeFormSaveRequest(null, null, null, request, null, null, null, null, null, null, null);
    }

    private NodeFormSaveRequest save(ContractDraftRequest request) {
        return new NodeFormSaveRequest(null, null, null, null, request, null, null, null, null, null, null);
    }

    private NodeFormSaveRequest save(AcceptanceDraftRequest request) {
        return new NodeFormSaveRequest(null, null, null, null, null, request, null, null, null, null, null);
    }

    private NodeFormSaveRequest save(NodeFormRuntimeRecordRequest request) {
        return new NodeFormSaveRequest(null, null, null, null, null, null, null, request, null, null, null);
    }

    private NodeFormSaveRequest save(NodeFormProjectRecordRequest request) {
        return new NodeFormSaveRequest(null, null, null, null, null, null, null, null, request, null, null);
    }
    private void setApplicationLimited(Long projectId, boolean limited) {
        ProjectApplication application = applicationDao.selectByProjectId(projectId);
        ProjectApplicationExt ext = projectStructuredDao.selectApplicationExtByApplicationId(application.getApplicationId());
        if (ext == null) {
            ext = new ProjectApplicationExt();
            ext.setApplicationId(application.getApplicationId());
            ext.setProjectId(projectId);
            ext.setCreatedAt(LocalDateTime.now());
        }
        ext.setIsLimitedProject(limited);
        ext.setUpdatedAt(LocalDateTime.now());
        if (ext.getApplicationExtId() == null) {
            projectStructuredDao.insertApplicationExt(ext);
        } else {
            projectStructuredDao.updateApplicationExt(ext);
        }
    }

    private void ensureAcceptance(Long projectId, Long moduleId, boolean schoolLevel) {
        ProjectAcceptance acceptance = acceptanceDao.selectByProjectId(projectId);
        if (acceptance == null) {
            acceptance = new ProjectAcceptance();
            acceptance.setProjectId(projectId);
            acceptance.setSubmittedAt(LocalDateTime.now());
            acceptance.setConclusion("Pending");
            acceptanceDao.insert(acceptance);
        }
        ProjectAcceptanceExt ext = projectStructuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId());
        if (ext == null) {
            ext = new ProjectAcceptanceExt();
            ext.setAcceptanceId(acceptance.getAcceptanceId());
            ext.setProjectId(projectId);
            ext.setCreatedAt(LocalDateTime.now());
        }
        ext.setModuleInstanceId(moduleId);
        ext.setIsSchoolLevelAcceptance(schoolLevel);
        ext.setUpdatedAt(LocalDateTime.now());
        if (ext.getAcceptanceExtId() == null) {
            projectStructuredDao.insertAcceptanceExt(ext);
        } else {
            projectStructuredDao.updateAcceptanceExt(ext);
        }
    }

    private boolean isSchoolLevel(Long projectId) {
        ProjectAcceptance acceptance = acceptanceDao.selectByProjectId(projectId);
        return Boolean.TRUE.equals(projectStructuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId()).getIsSchoolLevelAcceptance());
    }

    private void grantExpert(Long projectId, String moduleType, String nodeId, int roundNo) {
        grant(projectId, moduleType, ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT, expert.userId(), roundNo, nodeId);
    }

    private void grantFinance(Long projectId) {
        grant(projectId, null, ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT, financeAdmin.userId(), null, null);
    }

    private void grant(Long projectId, String moduleType, String grantRoleCode, Long userId, Integer roundNo, String nodeId) {
        ProjectRoleGrant grant = new ProjectRoleGrant();
        grant.setProjectId(projectId);
        grant.setModuleType(moduleType);
        grant.setGrantRoleCode(grantRoleCode);
        grant.setGranteeUserId(userId);
        grant.setGrantedByUserId(scienceAdmin.userId());
        grant.setGrantScope("WHITEBOX");
        grant.setRoundNo(roundNo);
        grant.setTaskNodeId(nodeId);
        grant.setStatus("ACTIVE");
        grant.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        grant.setGrantReason("whitebox path coverage");
        projectRoleGrantDao.insert(grant);
    }


    private void createExpertAssignmentBatch(PathRun run, String reviewType) {
        RuntimeViewResponse view = runtimeService.runtimeView(scienceAdmin, run.moduleId);
        ExpertReviewBatch batch = new ExpertReviewBatch();
        batch.setModuleInstanceId(run.moduleId);
        batch.setRoundNo(currentRound(run.moduleId));
        batch.setWorkflowNodeId(view.context().getCurrentWorkflowNodeId());
        batch.setReviewType(reviewType);
        batch.setReviewTitle(reviewType);
        batch.setRuleType("AVERAGE");
        batch.setMinExpertCount(1);
        batch.setPassScore(new BigDecimal("70"));
        batch.setRecommendScore(new BigDecimal("85"));
        batch.setRemoveHighestLowest(false);
        batch.setExpectedExpertCount(1);
        batch.setSubmittedExpertCount(1);
        batch.setValidExpertCount(1);
        batch.setStatus("IN_PROGRESS");
        batch.setCreatedBy(scienceAdmin.userId());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        expertReviewBatchDao.insert(batch);

        ExpertReviewAssignment assignment = new ExpertReviewAssignment();
        assignment.setBatchId(batch.getBatchId());
        assignment.setExpertUserId(expert.userId());
        assignment.setExpertName("Whitebox Expert");
        assignment.setExpertOrg("Whitebox Org");
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setReviewStatus("SUBMITTED");
        assignment.setTotalScore(new BigDecimal("90"));
        assignment.setReviewResult("PASSED");
        assignment.setSubmittedAt(LocalDateTime.now());
        assignment.setConflictOfInterest(false);
        assignment.setIsValid(true);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setUpdatedAt(LocalDateTime.now());
        expertReviewAssignmentDao.insert(assignment);
    }
    private void createExpertBatchResult(Long moduleId, String reviewType, String finalResult) {
        ExpertReviewBatch batch = new ExpertReviewBatch();
        batch.setModuleInstanceId(moduleId);
        batch.setRoundNo(currentRound(moduleId));
        batch.setReviewType(reviewType);
        batch.setReviewTitle(reviewType);
        batch.setRuleType("AVERAGE");
        batch.setMinExpertCount(1);
        batch.setPassScore(new BigDecimal("70"));
        batch.setRecommendScore(new BigDecimal("85"));
        batch.setRemoveHighestLowest(false);
        batch.setExpectedExpertCount(1);
        batch.setSubmittedExpertCount(1);
        batch.setValidExpertCount(1);
        batch.setFinalScore("REJECTED".equals(finalResult) ? new BigDecimal("50") : new BigDecimal("90"));
        batch.setFinalResult(finalResult);
        batch.setStatus("COMPLETED");
        batch.setCreatedBy(scienceAdmin.userId());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setCompletedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        expertReviewBatchDao.insert(batch);
    }

    private record PathRun(Long projectId, Long moduleId, Set<String> events) {
        PathRun(Long projectId, Long moduleId) {
            this(projectId, moduleId, new HashSet<>());
        }
    }
}