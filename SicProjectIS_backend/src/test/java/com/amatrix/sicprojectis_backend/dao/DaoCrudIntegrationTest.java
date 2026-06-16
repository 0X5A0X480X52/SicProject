package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewAssignmentDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewScoreDao;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewAssignment;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewScore;
import com.amatrix.sicprojectis_backend.material.dao.MaterialContextViewDao;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectContractDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordContextView;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;
import com.amatrix.sicprojectis_backend.system.dao.DepartmentDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.Department;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeConfigViewDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowDefinition;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeConfigView;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DaoCrudIntegrationTest {

    @Autowired
    private DepartmentDao departmentDao;

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private ProjectApplicationDao projectApplicationDao;

    @Autowired
    private ProjectContractDao projectContractDao;

    @Autowired
    private ProjectAcceptanceDao projectAcceptanceDao;

    @Autowired
    private ExpertReviewBatchDao expertReviewBatchDao;

    @Autowired
    private ExpertReviewAssignmentDao expertReviewAssignmentDao;

    @Autowired
    private ExpertReviewScoreDao expertReviewScoreDao;

    @Autowired
    private ProjectStructuredDataDao projectStructuredDataDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Autowired
    private UserRoleDetailViewDao userRoleDetailViewDao;

    @Autowired
    private WorkflowNodeConfigViewDao workflowNodeConfigViewDao;

    @Autowired
    private ModuleRuntimeContextViewDao moduleRuntimeContextViewDao;

    @Autowired
    private StateRecordContextViewDao stateRecordContextViewDao;

    @Autowired
    private MaterialContextViewDao materialContextViewDao;

    @Test
    void departmentDaoShouldSupportCrud() {
        Department department = new Department();
        department.setDeptCode("TEST_DEPT");
        department.setDeptName("Test Department");
        department.setEnabled(true);
        department.setCreatedAt(LocalDateTime.of(2026, 6, 2, 10, 0));
        department.setUpdatedAt(LocalDateTime.of(2026, 6, 2, 10, 0));

        assertThat(departmentDao.insert(department)).isEqualTo(1);
        assertThat(department.getDeptId()).isNotNull();

        Department persisted = departmentDao.selectById(department.getDeptId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getDeptCode()).isEqualTo("TEST_DEPT");

        department.setDeptName("Updated Department");
        assertThat(departmentDao.updateById(department)).isEqualTo(1);
        assertThat(departmentDao.selectById(department.getDeptId()).getDeptName()).isEqualTo("Updated Department");

        assertThat(departmentDao.deleteById(department.getDeptId())).isEqualTo(1);
        assertThat(departmentDao.selectById(department.getDeptId())).isNull();
    }

    @Test
    void projectDaoShouldSupportCrud() {
        Project project = new Project();
        project.setProjectCode("PRJ-TEST");
        project.setProjectName("DAO Test Project");
        project.setLeaderUserId(1L);
        project.setDeptId(1L);
        project.setProjectType("RESEARCH");
        project.setProjectLevel("PROVINCIAL");
        project.setApprovedAmount(new BigDecimal("52000.00"));
        project.setStartDate(LocalDate.of(2026, 6, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setLifecycleStage("APPLYING");
        project.setCreatedAt(LocalDateTime.of(2026, 6, 2, 10, 30));
        project.setUpdatedAt(LocalDateTime.of(2026, 6, 2, 10, 30));

        assertThat(projectDao.insert(project)).isEqualTo(1);
        assertThat(project.getProjectId()).isNotNull();

        Project persisted = projectDao.selectById(project.getProjectId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getProjectName()).isEqualTo("DAO Test Project");
        assertThat(persisted.getApprovedAmount()).isEqualByComparingTo("52000.00");

        project.setLifecycleStage("IN_REVIEW");
        assertThat(projectDao.updateById(project)).isEqualTo(1);
        assertThat(projectDao.selectById(project.getProjectId()).getLifecycleStage()).isEqualTo("IN_REVIEW");

        assertThat(projectDao.deleteById(project.getProjectId())).isEqualTo(1);
        assertThat(projectDao.selectById(project.getProjectId())).isNull();
    }

    @Test
    void businessDraftDaosShouldPersistStructuredDataWithoutStateRecords() {
        int stateRecordCountBefore = countRows("module_state_record");

        ProjectApplication application = new ProjectApplication();
        application.setProjectId(2L);
        application.setApplicationTitle("Draft Application");
        application.setIsLimitedProject(true);
        application.setApplicationSummary("Draft summary");
        assertThat(projectApplicationDao.insert(application)).isEqualTo(1);
        assertThat(application.getApplicationId()).isNotNull();
        ProjectApplicationExt applicationExt = new ProjectApplicationExt();
        applicationExt.setApplicationId(application.getApplicationId());
        applicationExt.setProjectId(2L);
        applicationExt.setApplicationCategory("RESEARCH");
        applicationExt.setExpectedBudget(new BigDecimal("120000.00"));
        applicationExt.setCreatedAt(LocalDateTime.of(2026, 6, 6, 8, 0));
        assertThat(projectStructuredDataDao.insertApplicationExt(applicationExt)).isEqualTo(1);
        ProjectApplicationDetail detail = new ProjectApplicationDetail();
        detail.setApplicationId(application.getApplicationId());
        detail.setProjectId(2L);
        detail.setResearchObjective("Draft goal");
        detail.setCreatedAt(LocalDateTime.of(2026, 6, 6, 8, 0));
        assertThat(projectStructuredDataDao.insertApplicationDetail(detail)).isEqualTo(1);
        detail.setResearchObjective("Updated draft goal");
        assertThat(projectStructuredDataDao.updateApplicationDetail(detail)).isEqualTo(1);
        assertThat(projectStructuredDataDao.selectApplicationDetailByApplicationId(application.getApplicationId()).getResearchObjective())
                .isEqualTo("Updated draft goal");

        ProjectContract contract = new ProjectContract();
        contract.setProjectId(2L);
        contract.setContractCode("CT-DRAFT");
        contract.setContractName("Draft Contract");
        contract.setContractAmount(new BigDecimal("120000.00"));
        contract.setContractStartDate(LocalDate.of(2026, 3, 1));
        contract.setContractEndDate(LocalDate.of(2026, 12, 31));
        contract.setSealStatus("DRAFT");
        assertThat(projectContractDao.insert(contract)).isEqualTo(1);
        ProjectContractExt contractExt = new ProjectContractExt();
        contractExt.setContractId(contract.getContractId());
        contractExt.setProjectId(2L);
        contractExt.setPartyAName("Authority");
        contractExt.setCreatedAt(LocalDateTime.of(2026, 6, 6, 8, 0));
        assertThat(projectStructuredDataDao.insertContractExt(contractExt)).isEqualTo(1);

        ProjectAcceptance acceptance = new ProjectAcceptance();
        acceptance.setProjectId(2L);
        acceptance.setConclusion("Draft acceptance");
        assertThat(projectAcceptanceDao.insert(acceptance)).isEqualTo(1);
        ProjectAcceptanceExt acceptanceExt = new ProjectAcceptanceExt();
        acceptanceExt.setAcceptanceId(acceptance.getAcceptanceId());
        acceptanceExt.setProjectId(2L);
        acceptanceExt.setTaskCompletionRate(new BigDecimal("80.00"));
        acceptanceExt.setCreatedAt(LocalDateTime.of(2026, 6, 6, 8, 0));
        assertThat(projectStructuredDataDao.insertAcceptanceExt(acceptanceExt)).isEqualTo(1);

        assertThat(countRows("module_state_record")).isEqualTo(stateRecordCountBefore);
    }

    @Test
    void expertReviewDaosShouldSupportBatchAssignmentsScoresAndUniqueness() {
        ExpertReviewBatch batch = new ExpertReviewBatch();
        batch.setModuleInstanceId(1L);
        batch.setWorkflowNodeId(1L);
        batch.setStateRecordId(1L);
        batch.setReviewType("APPLICATION_SCIENCE_EXPERT");
        batch.setReviewTitle("Science office expert review");
        batch.setRuleType("REMOVE_HIGHEST_LOWEST_AVERAGE");
        batch.setMinExpertCount(3);
        batch.setPassScore(new BigDecimal("70.00"));
        batch.setRecommendScore(new BigDecimal("85.00"));
        batch.setRemoveHighestLowest(true);
        batch.setExpectedExpertCount(5);
        batch.setSubmittedExpertCount(0);
        batch.setValidExpertCount(0);
        batch.setStatus("IN_PROGRESS");
        batch.setCreatedBy(3L);
        batch.setCreatedAt(LocalDateTime.of(2026, 6, 6, 9, 0));
        batch.setUpdatedAt(LocalDateTime.of(2026, 6, 6, 9, 0));

        assertThat(expertReviewBatchDao.insert(batch)).isEqualTo(1);
        assertThat(batch.getBatchId()).isNotNull();

        ExpertReviewAssignment assignment = new ExpertReviewAssignment();
        assignment.setBatchId(batch.getBatchId());
        assignment.setExpertUserId(5L);
        assignment.setExpertName("Evan Expert");
        assignment.setExpertOrg("Science Panel");
        assignment.setExpertTitle("Professor");
        assignment.setAssignedAt(LocalDateTime.of(2026, 6, 6, 9, 10));
        assignment.setReviewStatus("ASSIGNED");
        assignment.setConflictOfInterest(false);
        assignment.setIsValid(true);
        assignment.setCreatedAt(LocalDateTime.of(2026, 6, 6, 9, 10));

        assertThat(expertReviewAssignmentDao.insert(assignment)).isEqualTo(1);
        assertThat(assignment.getAssignmentId()).isNotNull();
        assertThat(expertReviewAssignmentDao.selectByBatchId(batch.getBatchId())).hasSize(1);

        ExpertReviewScore score = new ExpertReviewScore();
        score.setAssignmentId(assignment.getAssignmentId());
        score.setScoreItemCode("INNOVATION");
        score.setScoreItemName("Innovation");
        score.setWeight(new BigDecimal("1.00"));
        score.setMaxScore(new BigDecimal("100.00"));
        score.setScoreValue(new BigDecimal("92.50"));
        score.setComment("Strong innovation");
        score.setCreatedAt(LocalDateTime.of(2026, 6, 6, 9, 30));

        assertThat(expertReviewScoreDao.insert(score)).isEqualTo(1);
        assertThat(expertReviewScoreDao.selectByAssignmentId(assignment.getAssignmentId())).hasSize(1);

        assignment.setReviewStatus("SUBMITTED");
        assignment.setSubmittedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        assignment.setTotalScore(new BigDecimal("92.50"));
        assignment.setReviewResult("RECOMMENDED");
        assignment.setReviewComment("Recommended");
        assertThat(expertReviewAssignmentDao.updateById(assignment)).isEqualTo(1);
        assertThat(expertReviewAssignmentDao.selectById(assignment.getAssignmentId()).getReviewStatus())
                .isEqualTo("SUBMITTED");

        batch.setSubmittedExpertCount(1);
        batch.setValidExpertCount(1);
        batch.setHighestScore(new BigDecimal("92.50"));
        batch.setLowestScore(new BigDecimal("92.50"));
        batch.setFinalScore(new BigDecimal("92.50"));
        batch.setFinalResult("RECOMMENDED");
        batch.setSummaryComment("One expert submitted");
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(LocalDateTime.of(2026, 6, 6, 10, 5));
        assertThat(expertReviewBatchDao.updateById(batch)).isEqualTo(1);
        assertThat(expertReviewBatchDao.selectById(batch.getBatchId()).getFinalResult())
                .isEqualTo("RECOMMENDED");

        ExpertReviewAssignment duplicate = new ExpertReviewAssignment();
        duplicate.setBatchId(batch.getBatchId());
        duplicate.setExpertUserId(5L);
        duplicate.setAssignedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        duplicate.setReviewStatus("ASSIGNED");
        duplicate.setConflictOfInterest(false);
        duplicate.setIsValid(true);
        duplicate.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));

        assertThatThrownBy(() -> expertReviewAssignmentDao.insert(duplicate))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void workflowDefinitionDaoShouldSupportCrud() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setProcessKey("contract_review");
        workflowDefinition.setProcessName("Contract Review Flow");
        workflowDefinition.setModuleType("CONTRACT");
        workflowDefinition.setBpmnXml("<bpmn>contract</bpmn>");
        workflowDefinition.setStateMachineRulesJson("{\"initial\":\"DRAFT\"}");
        workflowDefinition.setVersionNo(2);
        workflowDefinition.setStatus("ACTIVE");
        workflowDefinition.setCreatedAt(LocalDateTime.of(2026, 6, 2, 11, 0));
        workflowDefinition.setUpdatedAt(LocalDateTime.of(2026, 6, 2, 11, 0));

        assertThat(workflowDefinitionDao.insert(workflowDefinition)).isEqualTo(1);
        assertThat(workflowDefinition.getWorkflowDefinitionId()).isNotNull();

        WorkflowDefinition persisted = workflowDefinitionDao.selectById(workflowDefinition.getWorkflowDefinitionId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getProcessKey()).isEqualTo("contract_review");

        workflowDefinition.setStatus("INACTIVE");
        assertThat(workflowDefinitionDao.updateById(workflowDefinition)).isEqualTo(1);
        assertThat(workflowDefinitionDao.selectById(workflowDefinition.getWorkflowDefinitionId()).getStatus()).isEqualTo("INACTIVE");

        assertThat(workflowDefinitionDao.deleteById(workflowDefinition.getWorkflowDefinitionId())).isEqualTo(1);
        assertThat(workflowDefinitionDao.selectById(workflowDefinition.getWorkflowDefinitionId())).isNull();
    }

    @Test
    void viewDaosShouldReturnSeededContextData() {
        UserRoleDetailView userRoleDetail = userRoleDetailViewDao.selectByUserId(1L).getFirst();
        assertThat(userRoleDetail.getUsername()).isEqualTo("alice");
        assertThat(userRoleDetail.getRoleCode()).isEqualTo("PROJECT_LEADER");

        WorkflowNodeConfigView workflowNodeConfig = workflowNodeConfigViewDao.selectByWorkflowNodeId(1L);
        assertThat(workflowNodeConfig).isNotNull();
        assertThat(workflowNodeConfig.getProcessKey()).isEqualTo("project_apply");
        assertThat(workflowNodeConfig.getStateCode()).isEqualTo("SUBMITTED");

        ModuleRuntimeContextView runtimeContext = moduleRuntimeContextViewDao.selectByModuleInstanceId(1L);
        assertThat(runtimeContext).isNotNull();
        assertThat(runtimeContext.getCurrentState()).isEqualTo("SUBMITTED");
        assertThat(runtimeContext.getCurrentNodeName()).isEqualTo("Start Apply");

        StateRecordContextView stateRecordContext = stateRecordContextViewDao.selectByStateRecordId(1L).getFirst();
        assertThat(stateRecordContext.getOperatorName()).isEqualTo("Alice Zhang");
        assertThat(stateRecordContext.getOperatorRemark()).isEqualTo("Submission completed");

        MaterialContextView materialContext = materialContextViewDao.selectByMaterialVersionId(1L);
        assertThat(materialContext).isNotNull();
        assertThat(materialContext.getFileName()).isEqualTo("application-form-v1.pdf");
        assertThat(materialContext.getUploadedByName()).isEqualTo("Alice Zhang");
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "`", Integer.class);
    }
}
