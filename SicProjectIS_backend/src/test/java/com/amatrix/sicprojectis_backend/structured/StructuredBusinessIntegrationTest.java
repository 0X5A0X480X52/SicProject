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
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StructuredBusinessIntegrationTest {
    @Autowired private StructuredBusinessService businessService;
    @Autowired private ExpertReviewService expertReviewService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final AuthenticatedUser leader = new AuthenticatedUser(1L, "leader", List.of("PROJECT_LEADER"));

    @Test
    void repeatedApplicationDraftSaveShouldOnlyUpdateStructuredBusinessTables() {
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class);
        ProjectApplication application = new ProjectApplication(); application.setApplicationTitle("Structured draft"); application.setApplicationSummary("First version");
        ProjectApplicationExt ext = new ProjectApplicationExt(); ext.setApplicationCategory("RESEARCH"); ext.setExpectedBudget(new BigDecimal("100000"));
        ProjectApplicationDetail detail = new ProjectApplicationDetail(); detail.setResearchObjective("First objective");
        businessService.saveApplication(leader, 1L, new ApplicationDraftRequest(application, ext, detail));
        detail.setResearchObjective("Updated objective");
        businessService.saveApplication(leader, 1L, new ApplicationDraftRequest(application, ext, detail));
        assertThat(businessService.getApplication(leader, 1L).detail().getResearchObjective()).isEqualTo("Updated objective");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class)).isEqualTo(before);
    }

    @Test
    void fiveValidExpertScoresShouldRemoveHighestAndLowest() {
        var batch = expertReviewService.create(leader, new CreateExpertReviewBatchRequest(1L, null, "APPLICATION_SCIENCE_EXPERT", "Review", "REMOVE_HIGHEST_LOWEST_AVERAGE", 3, new BigDecimal("70"), new BigDecimal("85"), true, 5)).batch();
        List<BigDecimal> values = List.of(new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("100"));
        for (int index=0; index<values.size(); index++) {
            var assignment = expertReviewService.assign(batch.getBatchId(), new AssignExpertRequest(100L+index, "Expert "+index, "Panel", "Professor")).assignments().getLast().assignment();
            expertReviewService.submit(assignment.getAssignmentId(), new SubmitExpertScoreRequest(false, true, "PASS", "ok", List.of(new SubmitExpertScoreRequest.ScoreItem("TOTAL", "Total", BigDecimal.ONE, new BigDecimal("100"), values.get(index), null))));
        }
        var completed = expertReviewService.detail(batch.getBatchId()).batch();
        assertThat(completed.getFinalScore()).isEqualByComparingTo("80.00");
        assertThat(completed.getValidExpertCount()).isEqualTo(5);
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    }
}
