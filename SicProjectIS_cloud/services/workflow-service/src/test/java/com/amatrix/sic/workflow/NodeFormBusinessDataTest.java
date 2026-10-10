package com.amatrix.sic.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import tools.jackson.databind.ObjectMapper;

class NodeFormBusinessDataTest {
    private JdbcTemplate jdbc;
    private NodeFormService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:node-form-business-data;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE project_application(module_instance_id BIGINT,payload_json CLOB)");
        jdbc.execute("CREATE TABLE contract_form(module_instance_id BIGINT,round_no INT,payload_json CLOB)");
        jdbc.execute("CREATE TABLE acceptance_form(module_instance_id BIGINT,round_no INT,payload_json CLOB)");
        jdbc.execute("CREATE TABLE workflow_node_config(id BIGINT PRIMARY KEY,node_id VARCHAR(100))");
        jdbc.execute("""
                CREATE TABLE expert_review_batch(
                  id BIGINT PRIMARY KEY,module_instance_id BIGINT,workflow_node_id BIGINT,round_no INT,
                  review_type VARCHAR(64),review_title VARCHAR(200),rule_type VARCHAR(32),min_expert_count INT,
                  pass_score DECIMAL(8,2),recommend_score DECIMAL(8,2),remove_highest_lowest BOOLEAN,
                  expected_expert_count INT,submitted_expert_count INT,valid_expert_count INT,
                  lowest_score DECIMAL(8,2),highest_score DECIMAL(8,2),final_score DECIMAL(8,2),
                  final_result VARCHAR(32),status VARCHAR(32),created_by BIGINT,
                  created_at TIMESTAMP,updated_at TIMESTAMP,completed_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE expert_review_assignment(
                  id BIGINT PRIMARY KEY,batch_id BIGINT,expert_user_id BIGINT,
                  expert_name_snapshot VARCHAR(100),expert_org_snapshot VARCHAR(200),expert_title_snapshot VARCHAR(100),
                  review_status VARCHAR(32),conflict_of_interest BOOLEAN,valid BOOLEAN,total_score DECIMAL(8,2),
                  review_result VARCHAR(32),review_comment VARCHAR(2000),assigned_at TIMESTAMP,submitted_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE expert_review_score(
                  id BIGINT PRIMARY KEY,assignment_id BIGINT,item_code VARCHAR(64),item_name VARCHAR(200),
                  weight DECIMAL(8,4),max_score DECIMAL(8,2),score_value DECIMAL(8,2),comment VARCHAR(1000))
                """);
        service = new NodeFormService(jdbc, new ObjectMapper(), new ExpertReviewViewRepository(jdbc));
    }

    @Test
    void overlaysLiveExpertAssignmentsOnTheStoredBusinessPayload() {
        jdbc.update("INSERT INTO project_application VALUES (?,?)", 1L,
                "{\"applicationDraft\":{\"title\":\"Demo\"},\"expertReviews\":[]}");
        jdbc.update("INSERT INTO workflow_node_config VALUES (?,?)", 9L, "DeptExpertAssignTask");
        jdbc.update("""
                INSERT INTO expert_review_batch VALUES
                (30,1,9,1,'APPLICATION','Department review','AVERAGE',1,60,85,FALSE,
                 1,0,0,NULL,NULL,NULL,NULL,'IN_PROGRESS',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL)
                """);
        jdbc.update("""
                INSERT INTO expert_review_assignment VALUES
                (40,30,7,'EXP1','Science','Professor','ASSIGNED',FALSE,TRUE,NULL,NULL,NULL,CURRENT_TIMESTAMP,NULL)
                """);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service.businessData(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews = (List<Map<String, Object>>) result.get("expertReviews");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) reviews.getFirst().get("assignments");
        @SuppressWarnings("unchecked")
        Map<String, Object> assignment = (Map<String, Object>) assignments.getFirst().get("assignment");

        assertThat(((Map<?, ?>) result.get("applicationDraft")).get("title")).isEqualTo("Demo");
        assertThat(reviews).hasSize(1);
        assertThat(reviews.getFirst().get("reviewNodeId")).isEqualTo("DeptExpertReviewTask");
        assertThat(assignment).containsEntry("assignmentId", 40L)
                .containsEntry("expertUserId", 7L)
                .containsEntry("expertName", "EXP1")
                .containsEntry("reviewStatus", "ASSIGNED");
    }
}
