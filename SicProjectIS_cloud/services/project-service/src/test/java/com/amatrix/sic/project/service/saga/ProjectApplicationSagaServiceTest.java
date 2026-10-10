package com.amatrix.sic.project.service.saga;

import com.amatrix.sic.project.ProjectTestMapper;

import com.amatrix.sic.project.dto.ProjectDtos;
import com.amatrix.sic.project.client.WorkflowSagaClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ApplicationSagaResult;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.messaging.OutboxService;

import tools.jackson.databind.ObjectMapper;

class ProjectApplicationSagaServiceTest {
    private JdbcTemplate jdbc;
    private WorkflowSagaClient workflow;
    private InternalTokenProvider tokens;
    private OutboxService outbox;
    private ProjectApplicationSagaService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project-saga;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE project(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_code VARCHAR(64) UNIQUE,"
                + "project_name VARCHAR(200),project_type VARCHAR(64),project_level VARCHAR(64),"
                + "approved_amount DECIMAL(18,2),start_date DATE,end_date DATE,owner_user_id BIGINT,"
                + "owner_name_snapshot VARCHAR(100),dept_id BIGINT,dept_name_snapshot VARCHAR(100),"
                + "lifecycle_status VARCHAR(32),version BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE project_member(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT,user_id BIGINT,"
                + "member_role VARCHAR(64),responsibility VARCHAR(500),user_name_snapshot VARCHAR(100))");
        jdbc.execute("CREATE TABLE application_saga(saga_id CHAR(36) PRIMARY KEY,project_id BIGINT,actor_user_id BIGINT,"
                + "request_hash CHAR(64),status VARCHAR(32),module_instance_id BIGINT,response_json VARCHAR(10000),last_error VARCHAR(1000),"
                + "retry_count INT DEFAULT 0,next_retry_at TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        workflow = mock(WorkflowSagaClient.class);
        tokens = mock(InternalTokenProvider.class);
        outbox = mock(OutboxService.class);
        when(tokens.authorization()).thenReturn("Bearer service-token");
        service = new ProjectApplicationSagaService(ProjectTestMapper.create(dataSource,com.amatrix.sic.project.dao.ApplicationSagaMapper.class,"mapper/project/ApplicationSagaMapper.xml"),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), workflow, tokens, outbox,
                new ObjectMapper());
    }

    @Test
    void completesAndDeduplicatesAnIdenticalRecentRequest() {
        when(workflow.create(anyString(), anyString(), any())).thenAnswer(invocation -> {
            var command = (com.amatrix.sic.contracts.ApplicationSagaCommand) invocation.getArgument(2);
            return ApiResponse.ok(result(command.sagaId(), command.projectId(), 101L));
        });
        var first = service.start(7L, "Owner", 3L, "Science", "Bearer user", request("P-001"));
        var duplicate = service.start(7L, "Owner", 3L, "Science", "Bearer user", request("P-001"));

        assertThat(duplicate).isEqualTo(first);
        assertThat(value("SELECT lifecycle_status FROM project WHERE id=?", first.projectId())).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM application_saga WHERE project_id=?", first.projectId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project", Integer.class)).isEqualTo(1);
        verify(workflow, times(1)).create(anyString(), anyString(), any());
        verify(outbox, times(1)).enqueue(anyString(), any());
    }

    @Test
    void workflowFailureCompensatesAndHidesTheProvisionalProject() {
        when(workflow.create(anyString(), anyString(), any())).thenThrow(new IllegalStateException("workflow offline"));
        when(workflow.compensate(anyString(), any())).thenReturn(ApiResponse.ok(null));

        assertThatThrownBy(() -> service.start(7L, "Owner", 3L, "Science", "Bearer user", request("P-002")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("provisional project was compensated");
        assertThat(value("SELECT lifecycle_status FROM project WHERE project_code='P-002'"))
                .isEqualTo("FAILED");
        assertThat(value("SELECT status FROM application_saga WHERE project_id=(SELECT id FROM project WHERE project_code='P-002')"))
                .isEqualTo("COMPENSATED");
        verify(workflow).compensate(anyString(), any());
    }

    @Test
    void failedFinalConfirmationIsRecoveredFromWorkflowStatus() {
        when(workflow.create(anyString(), anyString(), any())).thenAnswer(invocation -> {
            var command = (com.amatrix.sic.contracts.ApplicationSagaCommand) invocation.getArgument(2);
            return ApiResponse.ok(result(command.sagaId(), command.projectId(), 303L));
        });
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).enqueue(anyString(), any());

        assertThatThrownBy(() -> service.start(7L, "Owner", 3L, "Science", "Bearer user", request("P-003")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("confirmation will be recovered");
        String sagaId = value("SELECT saga_id FROM application_saga WHERE project_id=(SELECT id FROM project WHERE project_code='P-003')");
        Long projectId = jdbc.queryForObject("SELECT id FROM project WHERE project_code='P-003'", Long.class);
        assertThat(value("SELECT status FROM application_saga WHERE saga_id=?", sagaId)).isEqualTo("WORKFLOW_CREATED");
        assertThat(value("SELECT lifecycle_status FROM project WHERE id=?", projectId)).isEqualTo("PROVISIONING");

        reset(outbox);
        when(workflow.status(anyString(), any())).thenReturn(ApiResponse.ok(result(UUID.fromString(sagaId), projectId, 303L)));
        service.recoverIncompleteConfirmations();

        assertThat(value("SELECT status FROM application_saga WHERE saga_id=?", sagaId)).isEqualTo("COMPLETED");
        assertThat(value("SELECT lifecycle_status FROM project WHERE id=?", projectId)).isEqualTo("ACTIVE");
        verify(outbox).enqueue(anyString(), any());
    }

    private ProjectDtos.StartProjectApplicationRequest request(String code) {
        return new ProjectDtos.StartProjectApplicationRequest(code, "Project " + code, "RESEARCH", "SCHOOL",
                new BigDecimal("1000.00"), null, null, "Application " + code, false, "summary");
    }

    private static ApplicationSagaResult result(UUID sagaId, Long projectId, Long moduleId) {
        StateRecordContract record = new StateRecordContract(1L, moduleId, 1, 1, "STARTED", null,
                "APPLICATION_DRAFT", null, "SubmitApplicationTask", null, null, null);
        return new ApplicationSagaResult(sagaId, projectId, moduleId,
                new StateTransitionContract(record, "SubmitApplicationTask", "APPLICATION_DRAFT", false), "CREATED");
    }

    private String value(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
