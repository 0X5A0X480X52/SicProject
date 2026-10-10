package com.amatrix.sic.workflow;

import com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService;
import com.amatrix.sic.workflow.service.runtime.WorkflowTransitionActionService;
import com.amatrix.sic.workflow.service.form.NodeFormService;
import com.amatrix.sic.workflow.dto.WorkflowDtos;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionCatalog;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator;
import com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import com.amatrix.sic.workflow.policy.WorkflowProjectAccessPolicy;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.MaterialReservationClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.MaterialReservationResult;
import com.amatrix.sic.contracts.ProjectAccessResult;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.ProcessConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sic.workflow.dto.WorkflowDtos.TransitionRequest;

import tools.jackson.databind.ObjectMapper;

class WorkflowExpectedSequenceConcurrencyTest {
    private JdbcTemplate jdbc;
    private WorkflowRuntimeService service;
    private MaterialReservationClient materials;
    private WorkflowInternalTokenProvider tokens;
    private WorkflowTransitionActionService actions;
    private OutboxService outbox;
    private WorkflowDefinitionCatalog definitions;
    private WorkflowOperationPolicy operationPolicy;
    private ProjectAccessClient projectAccess;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow-sequence;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE module_instance(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,started_by_user_id BIGINT,
                  module_code VARCHAR(64) NOT NULL,definition_id BIGINT NOT NULL,current_node_id VARCHAR(64) NOT NULL,
                  current_state VARCHAR(64) NOT NULL,current_seq INT NOT NULL,current_round INT NOT NULL,
                  finished BOOLEAN NOT NULL,version BIGINT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE workflow_task(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,module_instance_id BIGINT NOT NULL,node_id VARCHAR(64) NOT NULL,
                  assignee_user_id BIGINT,candidate_role VARCHAR(64),status VARCHAR(32) NOT NULL,round_no INT NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,completed_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE state_record(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,module_instance_id BIGINT NOT NULL,seq INT NOT NULL,
                  round_no INT NOT NULL,event_type VARCHAR(64),from_state VARCHAR(64),to_state VARCHAR(64),
                  from_node_id VARCHAR(64),to_node_id VARCHAR(64),result VARCHAR(64),summary VARCHAR(1000),
                  actor_user_id BIGINT,actor_name_snapshot VARCHAR(100),created_at TIMESTAMP,
                  UNIQUE(module_instance_id,seq))
                """);
        jdbc.execute("""
                CREATE TABLE workflow_material_reference(
                  module_instance_id BIGINT NOT NULL,seq INT NOT NULL,material_version_id BIGINT NOT NULL,
                  material_type_code VARCHAR(64) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE project_snapshot(project_id BIGINT PRIMARY KEY,owner_user_id BIGINT,
                  owner_name_snapshot VARCHAR(100),dept_id BIGINT,source_version BIGINT,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO project_snapshot(project_id,source_version) VALUES (100,0),(200,0),(300,0)");
        jdbc.update("""
                INSERT INTO module_instance(id,project_id,started_by_user_id,module_code,definition_id,
                  current_node_id,current_state,current_seq,current_round,finished)
                VALUES (1,100,42,'CONTRACT',10,'ReviewTask','UNDER_REVIEW',1,1,FALSE)
                """);
        jdbc.update("""
                INSERT INTO workflow_task(module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no)
                VALUES (1,'ReviewTask',42,'SCIENCE_ADMIN','OPEN',1)
                """);

        definitions = mock(WorkflowDefinitionCatalog.class);
        operationPolicy = mock(WorkflowOperationPolicy.class);
        materials = mock(MaterialReservationClient.class);
        tokens = mock(WorkflowInternalTokenProvider.class);
        actions = mock(WorkflowTransitionActionService.class);
        outbox = mock(OutboxService.class);
        projectAccess = mock(ProjectAccessClient.class);
        when(definitions.model(10L)).thenReturn(model());
        when(operationPolicy.exactCanOperate(any(ActorContext.class), eq(1L))).thenReturn(true);
        when(tokens.authorization()).thenReturn("Bearer workflow-token");
        when(projectAccess.decideBatch(anyString(), any())).thenAnswer(invocation -> {
            var request = invocation.<com.amatrix.sic.contracts.ProjectAccessBatchRequest>getArgument(1);
            return ApiResponse.ok(request.targets().stream().map(target -> new ProjectAccessResult(
                    target.decisionId(), target.projectId(), true, 9L, "ALLOWED", 77L,
                    "project-owner", 1L)).toList());
        });

        service = new WorkflowRuntimeService(
                WorkflowTestMapper.create(dataSource,com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper.class,"mapper/workflow/WorkflowRuntimeMapper.xml"),
                WorkflowTestMapper.create(dataSource,com.amatrix.sic.workflow.dao.ProjectSnapshotMapper.class,"mapper/workflow/ProjectSnapshotMapper.xml"),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                projectAccess, materials, tokens,
                operationPolicy, definitions, new WorkflowDefinitionNavigator(), actions,
                mock(NodeFormService.class), outbox, new ObjectMapper(), mock(WorkflowProjectAccessPolicy.class),
                new ModuleSuccessorPolicy());
    }

    @Test
    void onlyOneOfTwoRequestsWithTheSameExpectedSequenceCommits() throws Exception {
        ActorContext actor = new ActorContext(42L, "science-admin", 1L, "Science",
                List.of("SCIENCE_ADMIN"), List.of(), "token", 1);
        TransitionRequest request = new TransitionRequest("SUBMIT", 1, "APPROVED", "done",
                List.of(), null, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> attempt = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                return service.transition(1L, actor, request);
            } catch (RuntimeException exception) {
                return exception;
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        List<Object> results;
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(results.stream().filter(StateTransitionContract.class::isInstance).count())
                .as("concurrent outcomes: %s", results.stream().map(WorkflowExpectedSequenceConcurrencyTest::outcome)
                        .toList())
                .isEqualTo(1);
        List<ResponseStatusException> conflicts = results.stream()
                .filter(ResponseStatusException.class::isInstance)
                .map(ResponseStatusException.class::cast)
                .toList();
        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getReason()).contains("sequence has changed");
        });

        assertThat(jdbc.queryForObject("SELECT current_seq FROM module_instance WHERE id=1", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM state_record WHERE module_instance_id=1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task WHERE status='COMPLETED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task WHERE status='OPEN'", Integer.class))
                .isEqualTo(1);
        verify(actions, times(1)).execute(anyLong(), anyLong(), any(), any(), any(), any(), anyList(), eq(false), any(),
                eq(null), eq(null));
        verify(outbox, times(1)).enqueue(eq("workflow.module-state-changed"), any());
        verifyNoInteractions(materials);
    }

    @Test
    void outboxFailureRollsBackTheWorkflowAndReleasesTheMaterialReservation() {
        ActorContext actor = new ActorContext(42L, "science-admin", 1L, "Science",
                List.of("SCIENCE_ADMIN"), List.of(), "token", 1);
        TransitionRequest request = new TransitionRequest("SUBMIT", 1, "APPROVED", "done",
                List.of(500L), null, null);
        MaterialReservationResult reservation = new MaterialReservationResult("reservation-1", "RESERVED",
                Instant.now().plusSeconds(300), List.of(500L), Map.of(500L, "CONTRACT_REVIEW_FILE"));
        when(materials.reserve(eq("Bearer workflow-token"), any())).thenReturn(ApiResponse.ok(reservation));
        when(materials.release("Bearer workflow-token", "reservation-1")).thenReturn(ApiResponse.ok(null));
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox)
                .enqueue(eq("material.reservation.confirm"), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.transition(1L, actor, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox unavailable");

        assertThat(jdbc.queryForObject("SELECT current_seq FROM module_instance WHERE id=1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_node_id FROM module_instance WHERE id=1", String.class))
                .isEqualTo("ReviewTask");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM state_record", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task WHERE status='OPEN'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_material_reference", Integer.class)).isZero();
        verify(materials).release("Bearer workflow-token", "reservation-1");
    }

    @Test
    void completingApplicationCreatesExactlyOneContractAndItsFirstTask() {
        jdbc.update("""
                INSERT INTO module_instance(id,project_id,started_by_user_id,module_code,definition_id,
                  current_node_id,current_state,current_seq,current_round,finished)
                VALUES (3,200,42,'APPLICATION',20,'FinalTask','APPLICATION_FINAL_MATERIAL_SUBMITTING',4,1,FALSE)
                """);
        jdbc.update("""
                INSERT INTO workflow_task(module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no)
                VALUES (3,'FinalTask',42,'SCIENCE_ADMIN','OPEN',1)
                """);
        when(definitions.model(20L)).thenReturn(applicationCompletionModel());
        when(definitions.active("CONTRACT"))
                .thenReturn(new WorkflowDefinitionCatalog.ActiveDefinition(30L, contractStartModel()));
        when(operationPolicy.exactCanOperate(any(ActorContext.class), eq(3L))).thenReturn(true);

        ActorContext actor = new ActorContext(42L, "science-admin", 1L, "Science",
                List.of("SCIENCE_ADMIN"), List.of(), "token", 1);
        StateTransitionContract completed = service.transition(3L, actor,
                new TransitionRequest("FINAL_MATERIALS_SUBMITTED", 4, "PROCESS_COMPLETED", "done",
                        List.of(), null, null));

        assertThat(completed.finished()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM module_instance WHERE project_id=200 AND module_code='CONTRACT'",
                Integer.class)).isEqualTo(1);
        Long contractId = jdbc.queryForObject(
                "SELECT id FROM module_instance WHERE project_id=200 AND module_code='CONTRACT'", Long.class);
        assertThat(jdbc.queryForObject("SELECT current_state FROM module_instance WHERE id=?", String.class, contractId))
                .isEqualTo("CONTRACT_DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task WHERE module_instance_id=? AND status='OPEN'",
                Integer.class, contractId)).isEqualTo(1);
        verify(actions).execute(eq(200L), eq(3L), eq("APPLICATION"), any(), any(), any(), anyList(), eq(true),
                eq(actor), eq(new ModuleSuccessorPolicy.Completion("CONTRACT", "CONTRACT")), eq(contractId));
    }

    @Test
    void recoveryBackfillsAContractForAnAlreadyCompletedApplication() {
        jdbc.update("""
                INSERT INTO module_instance(id,project_id,started_by_user_id,module_code,definition_id,
                  current_node_id,current_state,current_seq,current_round,finished)
                VALUES (4,300,42,'APPLICATION',20,'ApprovedEndEvent','APPLICATION_APPROVED',5,1,TRUE)
                """);
        jdbc.update("""
                INSERT INTO state_record(module_instance_id,seq,round_no,event_type,from_state,to_state,
                  from_node_id,to_node_id,result,summary,actor_user_id,actor_name_snapshot,created_at)
                VALUES (4,5,1,'FINAL_MATERIALS_SUBMITTED','APPLICATION_FINAL_MATERIAL_SUBMITTING',
                  'APPLICATION_APPROVED','FinalTask','ApprovedEndEvent','PROCESS_COMPLETED','done',42,
                  'science-admin',CURRENT_TIMESTAMP)
                """);
        when(definitions.active("CONTRACT"))
                .thenReturn(new WorkflowDefinitionCatalog.ActiveDefinition(30L, contractStartModel()));

        service.recoverMissingSuccessors();
        service.recoverMissingSuccessors();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM module_instance WHERE project_id=300 AND module_code='CONTRACT'",
                Integer.class)).isEqualTo(1);
        Long contractId = jdbc.queryForObject(
                "SELECT id FROM module_instance WHERE project_id=300 AND module_code='CONTRACT'", Long.class);
        verify(actions).enqueueLifecycle(eq(300L), eq(4L), eq("APPLICATION"), any(), any(),
                eq(new ModuleSuccessorPolicy.Completion("CONTRACT", "CONTRACT")), eq(contractId));
    }

    private static WorkflowBpmnParseResult model() {
        NodeConfig current = new NodeConfig("ReviewTask", "Review", "USER_TASK", "UNDER_REVIEW",
                null, null, null, "SCIENCE_ADMIN", null, null, null, List.of(), List.of());
        NodeConfig target = new NodeConfig("ArchiveTask", "Archive", "USER_TASK", "ARCHIVING",
                null, null, null, "SCIENCE_ADMIN", null, null, null, List.of(), List.of());
        TransitionConfig transition = new TransitionConfig("review-to-archive", "ReviewTask", "ArchiveTask",
                "UNDER_REVIEW", "ARCHIVING", "SUBMIT", "APPROVED", "NONE",
                null, null, null, null, List.of());
        return new WorkflowBpmnParseResult(
                new ProcessConfig("CONTRACT", "Contract", "CONTRACT", 1, "PUBLISHED"),
                List.of(current, target), List.of(transition));
    }

    private static WorkflowBpmnParseResult applicationCompletionModel() {
        NodeConfig current = new NodeConfig("FinalTask", "Final materials", "USER_TASK",
                "APPLICATION_FINAL_MATERIAL_SUBMITTING", null, null, null, "SCIENCE_ADMIN", null, null, null,
                List.of(), List.of());
        NodeConfig end = new NodeConfig("ApprovedEndEvent", "Approved", "END_EVENT", "APPLICATION_APPROVED",
                null, null, null, null, null, null, null, List.of(), List.of());
        TransitionConfig transition = new TransitionConfig("application-finish", "FinalTask", "ApprovedEndEvent",
                "APPLICATION_FINAL_MATERIAL_SUBMITTING", "APPLICATION_APPROVED", "FINAL_MATERIALS_SUBMITTED",
                "PROCESS_COMPLETED", "NONE", null, null, null, null, List.of("GENERATE_APPLICATION_DOCUMENTS"));
        return new WorkflowBpmnParseResult(
                new ProcessConfig("APPLICATION", "Application", "APPLICATION", 1, "PUBLISHED"),
                List.of(current, end), List.of(transition));
    }

    private static WorkflowBpmnParseResult contractStartModel() {
        NodeConfig start = new NodeConfig("StartEvent", "Start", "START_EVENT", "CONTRACT_START",
                null, null, null, null, null, null, null, List.of(), List.of());
        NodeConfig draft = new NodeConfig("FillContractTask", "Fill contract", "USER_TASK", "CONTRACT_DRAFT",
                null, null, null, "PROJECT_LEADER", null, null, null, List.of(), List.of());
        TransitionConfig transition = new TransitionConfig("contract-start", "StartEvent", "FillContractTask",
                "CONTRACT_START", "CONTRACT_DRAFT", "CONTRACT_PROCESS_STARTED", "STARTED", "NONE",
                null, null, null, null, List.of());
        return new WorkflowBpmnParseResult(
                new ProcessConfig("CONTRACT", "Contract", "CONTRACT", 1, "PUBLISHED"),
                List.of(start, draft), List.of(transition));
    }

    private static String outcome(Object value) {
        if (value instanceof Throwable failure) {
            return failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return value.getClass().getSimpleName() + ": " + value;
    }
}
