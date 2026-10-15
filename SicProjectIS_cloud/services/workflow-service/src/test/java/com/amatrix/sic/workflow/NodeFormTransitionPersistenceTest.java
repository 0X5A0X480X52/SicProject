package com.amatrix.sic.workflow;

import com.amatrix.sic.workflow.service.form.NodeFormService;
import com.amatrix.sic.workflow.dao.review.ExpertReviewViewRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class NodeFormTransitionPersistenceTest {
    JdbcTemplate jdbc;
    NodeFormService forms;
    TransactionTemplate transaction;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:forms-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE node_form(id BIGINT AUTO_INCREMENT PRIMARY KEY,module_instance_id BIGINT,node_id VARCHAR(64),round_no INT,form_json CLOB,submitted_by BIGINT,UNIQUE(module_instance_id,node_id,round_no))");
        jdbc.execute("CREATE TABLE project_application(module_instance_id BIGINT UNIQUE,project_id BIGINT,title VARCHAR(200),limited_project BOOLEAN,summary CLOB,payload_json CLOB)");
        jdbc.execute("CREATE TABLE contract_form(module_instance_id BIGINT,round_no INT,payload_json CLOB,UNIQUE(module_instance_id,round_no))");
        jdbc.execute("CREATE TABLE acceptance_form(module_instance_id BIGINT,round_no INT,payload_json CLOB,UNIQUE(module_instance_id,round_no))");
        var reviews = mock(ExpertReviewViewRepository.class);
        when(reviews.moduleReviews(anyLong())).thenReturn(List.of());
        forms = new NodeFormService(WorkflowTestMapper.create(jdbc.getDataSource(),com.amatrix.sic.workflow.dao.NodeFormMapper.class,"mapper/workflow/NodeFormMapper.xml"),new ObjectMapper(),reviews,null);
        jdbc.update("INSERT INTO project_application VALUES (1,10,'Old',FALSE,NULL,'{}')");
    }

    @Test void frontendTransitionWritesReadableFormAndBusinessDraftTogether() {
        var draft = Map.of("application",Map.of("applicationTitle","New","isLimitedProject",true,"applicationSummary","Summary"),"detail",Map.of("researchContent","Research"));
        transaction.executeWithoutResult(status -> forms.persistTransition(1L,10L,"APPLICATION","SubmitApplicationTask",
                "APPLICATION_DRAFT",1,"PROJECT_APPLICATION_FORM",Map.of("projectId",10L,"moduleInstanceId",1L,"applicationDraft",draft),7L));
        assertThat(forms.get("PROJECT_APPLICATION_FORM",10L,1L,null).get("applicationDraft")).isEqualTo(draft);
        assertThat(((Map<?,?>)forms.businessData(1L)).get("application")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT title FROM project_application",String.class)).isEqualTo("New");
        assertThat(jdbc.queryForObject("SELECT limited_project FROM project_application",Boolean.class)).isTrue();
    }

    @Test void rollbackLeavesNeitherFormNorDraftChange() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            forms.persistTransition(1L,10L,"APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT",1,
                    "PROJECT_APPLICATION_FORM",Map.of("applicationDraft",Map.of("application",Map.of("applicationTitle","New"))),7L);
            throw new IllegalStateException("Transition failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM node_form",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM project_application",String.class)).isEqualTo("Old");
    }

    @Test void historyRecordsRemainDistinctAcrossRoundsAndReadLegacyNodeKeys() {
        for (int round : List.of(1,2)) {
            int current = round;
            transaction.executeWithoutResult(status -> forms.persistTransition(1L,10L,"APPLICATION","DeptReviewTask",
                "APPLICATION_DEPT_REVIEWING",current,"DEPT_APPLICATION_REVIEW_FORM",
                Map.of("runtimeRecord",Map.of("checkItem",Map.of("passed",true,"remark","round"+current))),7L));
        }
        jdbc.update("INSERT INTO node_form(module_instance_id,node_id,round_no,form_json,submitted_by) VALUES(1,'DeptReviewTask',3,'{\"runtimeRecord\":{\"checkItem\":{\"remark\":\"legacy\"}}}',7)");
        assertThat((List<?>)forms.get("DEPT_APPLICATION_REVIEW_FORM",10L,1L,null).get("checkItems")).hasSize(3);
    }

    @Test void mismatchedFormOrProjectIsRejectedBeforeWriting() {
        assertThatThrownBy(() -> forms.persistTransition(1L,10L,"APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT",1,
            "DEPT_APPLICATION_REVIEW_FORM",Map.of(),7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> forms.persistTransition(1L,10L,"APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT",1,
            "PROJECT_APPLICATION_FORM",Map.of("projectId",20L),7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM node_form",Integer.class)).isZero();
    }
}
