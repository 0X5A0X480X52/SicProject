package com.amatrix.sicprojectis_backend.structured;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.amatrix.sicprojectis_backend.expert.ExpertReviewService;
import com.amatrix.sicprojectis_backend.expert.dto.AssignExpertRequest;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.expert.dto.SubmitExpertScoreRequest;
import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftRequest;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StructuredBusinessIntegrationTest {
    @Autowired private StructuredBusinessService businessService;
    @Autowired private ExpertReviewService expertReviewService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final AuthenticatedUser leader = new AuthenticatedUser(1L, "leader", List.of("PROJECT_LEADER"));

    @Test
    void repeatedApplicationDraftSaveShouldOnlyPatchProvidedFields() {
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class);
        ProjectApplication application = new ProjectApplication(); application.setApplicationTitle("Structured draft"); application.setApplicationSummary("First version"); application.setIsLimitedProject(true);
        ProjectApplicationExt ext = new ProjectApplicationExt(); ext.setApplicationCategory("RESEARCH"); ext.setExpectedBudget(new BigDecimal("100000"));
        ProjectApplicationDetail detail = new ProjectApplicationDetail(); detail.setResearchObjective("First objective");
        businessService.saveApplication(leader, 1L, new ApplicationDraftRequest(application, ext, detail));

        ProjectApplication patch = new ProjectApplication(); patch.setApplicationTitle("Updated title");
        ProjectApplicationDetail detailPatch = new ProjectApplicationDetail(); detailPatch.setApplicantCommitment("Committed");
        businessService.saveApplication(leader, 1L, new ApplicationDraftRequest(patch, new ProjectApplicationExt(), detailPatch));

        var response = businessService.getApplication(leader, 1L);
        assertThat(response.application().getApplicationTitle()).isEqualTo("Updated title");
        assertThat(response.application().getIsLimitedProject()).isTrue();
        assertThat(response.extension().getApplicationCategory()).isEqualTo("RESEARCH");
        assertThat(response.extension().getExpectedBudget()).isEqualByComparingTo("100000");
        assertThat(response.detail().getResearchObjective()).isEqualTo("First objective");
        assertThat(response.detail().getApplicantCommitment()).isEqualTo("Committed");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class)).isEqualTo(before);
    }

    @Test
    void repeatedContractDraftSaveShouldOnlyPatchProvidedFields() {
        ProjectContract contract = new ProjectContract(); contract.setContractCode("C-001"); contract.setContractName("Contract v1"); contract.setContractAmount(new BigDecimal("50000")); contract.setSealStatus("DRAFT");
        ProjectContractExt ext = new ProjectContractExt(); ext.setContractSource("AUTHORITY"); ext.setPartyAName("Party A");
        businessService.saveContract(leader, 1L, new ContractDraftRequest(contract, ext));

        ProjectContract patch = new ProjectContract(); patch.setContractName("Contract v2");
        ProjectContractExt extPatch = new ProjectContractExt(); extPatch.setPartyBName("Party B");
        businessService.saveContract(leader, 1L, new ContractDraftRequest(patch, extPatch));

        var response = businessService.getContract(leader, 1L);
        assertThat(response.contract().getContractName()).isEqualTo("Contract v2");
        assertThat(response.contract().getContractCode()).isEqualTo("C-001");
        assertThat(response.contract().getContractAmount()).isEqualByComparingTo("50000");
        assertThat(response.contract().getSealStatus()).isEqualTo("DRAFT");
        assertThat(response.extension().getContractSource()).isEqualTo("AUTHORITY");
        assertThat(response.extension().getPartyAName()).isEqualTo("Party A");
        assertThat(response.extension().getPartyBName()).isEqualTo("Party B");
    }

    @Test
    void repeatedAcceptanceDraftSaveShouldOnlyPatchProvidedFields() {
        ProjectAcceptance acceptance = new ProjectAcceptance(); acceptance.setCertificateNo("CERT-001"); acceptance.setConclusion("Initial conclusion");
        ProjectAcceptanceExt ext = new ProjectAcceptanceExt(); ext.setIsSchoolLevelAcceptance(true); ext.setAcceptanceType("SCHOOL"); ext.setTaskCompletionRate(new BigDecimal("80.00"));
        businessService.saveAcceptance(leader, 1L, new AcceptanceDraftRequest(acceptance, ext));

        ProjectAcceptance patch = new ProjectAcceptance(); patch.setConclusion("Updated conclusion");
        ProjectAcceptanceExt extPatch = new ProjectAcceptanceExt(); extPatch.setPaperCount(2);
        businessService.saveAcceptance(leader, 1L, new AcceptanceDraftRequest(patch, extPatch));

        var response = businessService.getAcceptance(leader, 1L);
        assertThat(response.acceptance().getConclusion()).isEqualTo("Updated conclusion");
        assertThat(response.acceptance().getCertificateNo()).isEqualTo("CERT-001");
        assertThat(response.extension().getIsSchoolLevelAcceptance()).isTrue();
        assertThat(response.extension().getAcceptanceType()).isEqualTo("SCHOOL");
        assertThat(response.extension().getTaskCompletionRate()).isEqualByComparingTo("80.00");
        assertThat(response.extension().getPaperCount()).isEqualTo(2);
    }

    @Test
    void fiveValidExpertScoresShouldRemoveHighestAndLowest() {
        var batch = expertReviewService.create(leader, new CreateExpertReviewBatchRequest(1L, null, "APPLICATION_SCIENCE_EXPERT", "Review", "REMOVE_HIGHEST_LOWEST_AVERAGE", 3, new BigDecimal("70"), new BigDecimal("85"), true, 5)).batch();
        List<BigDecimal> values = List.of(new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("100"));
        for (int index=0; index<values.size(); index++) {
            var assignment = expertReviewService.assign(leader, batch.getBatchId(), new AssignExpertRequest(100L+index, "Expert "+index, "Panel", "Professor")).assignments().getLast().assignment();
            expertReviewService.submit(assignment.getAssignmentId(), new SubmitExpertScoreRequest(false, true, "PASS", "ok", List.of(new SubmitExpertScoreRequest.ScoreItem("TOTAL", "Total", BigDecimal.ONE, new BigDecimal("100"), values.get(index), null))));
        }
        var completed = expertReviewService.detail(batch.getBatchId()).batch();
        assertThat(completed.getFinalScore()).isEqualByComparingTo("80.00");
        assertThat(completed.getValidExpertCount()).isEqualTo(5);
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    }
}
