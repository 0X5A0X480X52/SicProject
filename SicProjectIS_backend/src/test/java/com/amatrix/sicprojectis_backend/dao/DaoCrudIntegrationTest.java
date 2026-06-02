package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.Department;
import com.amatrix.sicprojectis_backend.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.entity.Project;
import com.amatrix.sicprojectis_backend.entity.StateRecordContextView;
import com.amatrix.sicprojectis_backend.entity.UserRoleDetailView;
import com.amatrix.sicprojectis_backend.entity.WorkflowDefinition;
import com.amatrix.sicprojectis_backend.entity.WorkflowNodeConfigView;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DaoCrudIntegrationTest {

    @Autowired
    private DepartmentDao departmentDao;

    @Autowired
    private ProjectDao projectDao;

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
        assertThat(userRoleDetail.getRoleCode()).isEqualTo("PI");

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
}
