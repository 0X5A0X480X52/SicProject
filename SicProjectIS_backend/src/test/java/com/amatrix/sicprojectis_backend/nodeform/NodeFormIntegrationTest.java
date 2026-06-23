package com.amatrix.sicprojectis_backend.nodeform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.material.MaterialService;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormExpertRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormProjectRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormContext;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRuntimeRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.structured.dto.AchievementRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.NoticeUpsertRequest;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NodeFormIntegrationTest {
    @Autowired
    private NodeFormService nodeFormService;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AuthenticatedUser leader = new AuthenticatedUser(1L, "alice", List.of("PROJECT_LEADER"));

    @Test
    void singleInstanceApplicationFormShouldUpdateDraftWithoutStateRecord() {
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class);
        ProjectApplication application = new ProjectApplication();
        application.setApplicationTitle("Node form draft");
        application.setApplicationSummary("Structured save");
        ProjectApplicationExt extension = new ProjectApplicationExt();
        extension.setExpectedBudget(new BigDecimal("120000"));
        ProjectApplicationDetail detail = new ProjectApplicationDetail();
        detail.setResearchObjective("Objective from node form");

        nodeFormService.save(leader, "PROJECT_APPLICATION_FORM",
                new NodeFormSaveRequest(1L, 1L, null,
                        new ApplicationDraftRequest(application, extension, detail),
                        null, null, null, null, null, null, null));

        var response = nodeFormService.get(leader, "PROJECT_APPLICATION_FORM", new NodeFormContext(1L, 1L, null));
        assertThat(response.applicationDraft().application().getApplicationTitle()).isEqualTo("Node form draft");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM module_state_record", Integer.class)).isEqualTo(before);
    }

    @Test
    void historyCheckItemShouldCreateUpdateAndDeleteRecord() {
        StateRecordCheckItem item = new StateRecordCheckItem();
        item.setItemCode("DEPT_OK");
        item.setItemName("Department check");
        item.setItemType("BOOLEAN");
        item.setPassed(true);
        item.setRequired(true);

        var created = nodeFormService.createRecord(leader, "DEPT_APPLICATION_REVIEW_FORM",
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(item, null, null, null, null),
                        null, null, null));

        item.setRemark("Updated");
        nodeFormService.updateRecord(leader, "DEPT_APPLICATION_REVIEW_FORM", created.recordId(),
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(item, null, null, null, null),
                        null, null, null));
        assertThat(jdbcTemplate.queryForObject("SELECT remark FROM state_record_check_item WHERE check_item_id=?",
                String.class, created.recordId())).isEqualTo("Updated");

        nodeFormService.deleteRecord(leader, "DEPT_APPLICATION_REVIEW_FORM", created.recordId(), new NodeFormContext(1L, 1L, 1L));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_record_check_item WHERE check_item_id=?",
                Integer.class, created.recordId())).isZero();
    }

    @Test
    void runtimeHistoryFormsShouldRejectMissingModuleOrStateContext() {
        assertBadRequest("APPLICATION_AUTHORITY_RESULT_FORM",
                new NodeFormSaveRequest(1L, null, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, externalResult(), null, null, null),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
        assertBadRequest("APPLICATION_AUTHORITY_RESULT_FORM",
                new NodeFormSaveRequest(1L, 1L, null, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, externalResult(), null, null, null),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
        assertBadRequest("APPLICATION_SEAL_FORM",
                new NodeFormSaveRequest(1L, 1L, null, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, sealRecord(), null, null),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
        assertBadRequest("APPLICATION_FINAL_SUBMISSION_FORM",
                new NodeFormSaveRequest(1L, 1L, null, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, null, submissionRecord(), null),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
        assertBadRequest("CONTRACT_ARCHIVE_FORM",
                new NodeFormSaveRequest(1L, 1L, null, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, null, null, archiveRecord()),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
        assertBadRequest("DEPT_APPLICATION_REVIEW_FORM",
                new NodeFormSaveRequest(1L, 1L, null, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(checkItem(), null, null, null, null),
                        null, null, null),
                "moduleInstanceId and stateRecordId are required");
    }

    @Test
    void runtimeHistoryFormsShouldCreateWithFullContext() {
        assertThat(nodeFormService.createRecord(leader, "APPLICATION_AUTHORITY_RESULT_FORM",
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, externalResult(), null, null, null),
                        null, null, null)).recordId()).isNotNull();
        assertThat(nodeFormService.createRecord(leader, "APPLICATION_SEAL_FORM",
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, sealRecord(), null, null),
                        null, null, null)).recordId()).isNotNull();
        assertThat(nodeFormService.createRecord(leader, "APPLICATION_FINAL_SUBMISSION_FORM",
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, null, submissionRecord(), null),
                        null, null, null)).recordId()).isNotNull();
        assertThat(nodeFormService.createRecord(leader, "CONTRACT_ARCHIVE_FORM",
                new NodeFormSaveRequest(1L, 1L, 1L, null, null, null, null,
                        new NodeFormRuntimeRecordRequest(null, null, null, null, archiveRecord()),
                        null, null, null)).recordId()).isNotNull();
    }

    @Test
    void noticeAndAchievementShouldKeepTheirLooserContextRules() {
        var notice = nodeFormService.createRecord(leader, "APPLICATION_NOTICE_FORM",
                new NodeFormSaveRequest(null, null, null, null, null, null,
                        new NoticeUpsertRequest("APPLICATION", "APPLICATION_NOTICE", "Notice", null, null,
                                null, null, null, null, null, null, null, null, null, null, null, null),
                        null, null, null, null));
        assertThat(notice.recordId()).isNotNull();

        var achievement = nodeFormService.createRecord(leader, "ACHIEVEMENT_LIST_FORM",
                new NodeFormSaveRequest(1L, null, null, null, null, null, null, null,
                        new NodeFormProjectRecordRequest(null, null,
                                new AchievementRequest(null, null, "PAPER", "Paper A", "Team", null, null, null, null),
                                null),
                        null, null));
        assertThat(achievement.recordId()).isNotNull();
    }

    @Test
    void expertReviewBatchShouldRejectMissingModuleContextAtNodeFormLayer() {
        assertBadRequest("EXPERT_ACCEPTANCE_REVIEW_FORM",
                new NodeFormSaveRequest(1L, null, null, null, null, null, null, null, null,
                new NodeFormExpertRequest(null, null,
                                new CreateExpertReviewBatchRequest(null, null, "ACCEPTANCE_EXPERT_REVIEWING",
                                        "Acceptance review", "AVERAGE", 3, null, null, false, 3),
                                null, null),
                        null),
                "moduleInstanceId is required");
    }

    @Test
    void materialUploadShouldCreateVersionAndProtectReferencedVersions() {
        MockMultipartFile file = new MockMultipartFile("file", "draft.pdf", "application/pdf", "hello".getBytes());
        var uploaded = materialService.upload(leader, 1L, "APP_FORM", file);

        assertThat(uploaded.version().getMaterialVersionId()).isNotNull();
        assertThat(uploaded.version().getVersionNo()).isEqualTo(2);
        assertThatThrownBy(() -> materialService.deleteVersion(leader, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        materialService.deleteVersion(leader, uploaded.version().getMaterialVersionId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_version WHERE material_version_id=?",
                Integer.class, uploaded.version().getMaterialVersionId())).isZero();
    }

    private void assertBadRequest(String formCode, NodeFormSaveRequest request, String message) {
        assertThatThrownBy(() -> nodeFormService.createRecord(leader, formCode, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining(message);
    }

    private StateRecordCheckItem checkItem() {
        StateRecordCheckItem item = new StateRecordCheckItem();
        item.setItemCode("CHECK");
        item.setItemName("Check");
        item.setItemType("BOOLEAN");
        item.setRequired(true);
        item.setPassed(true);
        return item;
    }

    private ExternalResultRecord externalResult() {
        ExternalResultRecord row = new ExternalResultRecord();
        row.setResultType("AUTHORITY_REVIEW");
        row.setExternalResult("APPROVED");
        return row;
    }

    private SealRecord sealRecord() {
        SealRecord row = new SealRecord();
        row.setSealSubject("Application materials");
        row.setLeaderSigned(false);
        row.setSchoolSealed(false);
        row.setExternalSealed(false);
        row.setSealStatus("PENDING");
        return row;
    }

    private SubmissionRecord submissionRecord() {
        SubmissionRecord row = new SubmissionRecord();
        row.setSubmissionType("FINAL");
        row.setSubmissionMethod("ONLINE");
        return row;
    }

    private ArchiveRecord archiveRecord() {
        ArchiveRecord row = new ArchiveRecord();
        row.setArchiveType("CONTRACT");
        row.setArchiveStatus("ARCHIVED");
        return row;
    }
}
