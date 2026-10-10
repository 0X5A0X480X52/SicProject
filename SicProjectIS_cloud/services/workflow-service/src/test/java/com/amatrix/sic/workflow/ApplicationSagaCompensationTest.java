package com.amatrix.sic.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.amatrix.sic.messaging.OutboxService;

import tools.jackson.databind.ObjectMapper;

class ApplicationSagaCompensationTest {
    private JdbcTemplate jdbc;
    private ApplicationSagaWorkflowService service;
    private OutboxService outbox;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow-saga-compensation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE module_instance(id BIGINT PRIMARY KEY,saga_id CHAR(36) UNIQUE)");
        jdbc.execute("CREATE TABLE state_record(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE workflow_task(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE node_form(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE project_application(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE contract_form(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE acceptance_form(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE expert_review(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE workflow_material_reference(module_instance_id BIGINT,material_version_id BIGINT)");
        jdbc.execute("CREATE TABLE expert_review_batch(id BIGINT PRIMARY KEY,module_instance_id BIGINT)");
        jdbc.execute("CREATE TABLE expert_review_assignment(id BIGINT PRIMARY KEY,batch_id BIGINT)");
        jdbc.execute("CREATE TABLE expert_review_score(id BIGINT PRIMARY KEY,assignment_id BIGINT)");

        outbox = mock(OutboxService.class);
        service = new ApplicationSagaWorkflowService(jdbc, new ObjectMapper(), outbox,
                mock(WorkflowDefinitionCatalog.class), mock(WorkflowDefinitionNavigator.class));
    }

    @Test
    void compensationCanBeRepeatedAndOnlyDeletesTheTargetSagaGraph() {
        UUID targetSaga = UUID.randomUUID();
        UUID unrelatedSaga = UUID.randomUUID();
        insertGraph(10L, targetSaga, 100L);
        insertGraph(20L, unrelatedSaga, 200L);

        service.compensate(targetSaga);
        service.compensate(targetSaga);

        assertThat(moduleCount(10L)).isZero();
        assertThat(count("state_record", 10L)).isZero();
        assertThat(count("workflow_task", 10L)).isZero();
        assertThat(count("node_form", 10L)).isZero();
        assertThat(count("project_application", 10L)).isZero();
        assertThat(count("contract_form", 10L)).isZero();
        assertThat(count("acceptance_form", 10L)).isZero();
        assertThat(count("expert_review", 10L)).isZero();
        assertThat(count("workflow_material_reference", 10L)).isZero();
        assertThat(count("expert_review_batch", 10L)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE id=101", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_score WHERE assignment_id=101", Integer.class))
                .isZero();

        assertThat(moduleCount(20L)).isOne();
        assertThat(count("state_record", 20L)).isOne();
        assertThat(count("workflow_task", 20L)).isOne();
        assertThat(count("node_form", 20L)).isOne();
        assertThat(count("project_application", 20L)).isOne();
        assertThat(count("contract_form", 20L)).isOne();
        assertThat(count("acceptance_form", 20L)).isOne();
        assertThat(count("expert_review", 20L)).isOne();
        assertThat(count("workflow_material_reference", 20L)).isOne();
        assertThat(count("expert_review_batch", 20L)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE id=201", Integer.class))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_score WHERE assignment_id=201", Integer.class))
                .isOne();
        verifyNoInteractions(outbox);
    }

    private void insertGraph(long moduleId, UUID sagaId, long idBase) {
        jdbc.update("INSERT INTO module_instance(id,saga_id) VALUES (?,?)", moduleId, sagaId.toString());
        for (String table : new String[] { "state_record", "workflow_task", "node_form", "project_application",
                "contract_form", "acceptance_form", "expert_review" }) {
            jdbc.update("INSERT INTO " + table + "(id,module_instance_id) VALUES (?,?)", idBase, moduleId);
        }
        jdbc.update("INSERT INTO workflow_material_reference(module_instance_id,material_version_id) VALUES (?,?)",
                moduleId, idBase);
        jdbc.update("INSERT INTO expert_review_batch(id,module_instance_id) VALUES (?,?)", idBase, moduleId);
        jdbc.update("INSERT INTO expert_review_assignment(id,batch_id) VALUES (?,?)", idBase + 1, idBase);
        jdbc.update("INSERT INTO expert_review_score(id,assignment_id) VALUES (?,?)", idBase + 2, idBase + 1);
    }

    private int count(String table, long moduleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE module_instance_id=?", Integer.class,
                moduleId);
    }

    private int moduleCount(long moduleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM module_instance WHERE id=?", Integer.class, moduleId);
    }
}
