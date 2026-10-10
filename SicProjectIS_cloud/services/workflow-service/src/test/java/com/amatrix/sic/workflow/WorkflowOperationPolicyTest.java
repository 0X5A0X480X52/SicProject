package com.amatrix.sic.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ProjectAccessResult;

class WorkflowOperationPolicyTest {
    private WorkflowOperationPolicy policy;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow-policy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE module_instance(id BIGINT PRIMARY KEY,project_id BIGINT,module_code VARCHAR(32),current_node_id VARCHAR(64),finished BOOLEAN)");
        jdbc.execute("CREATE TABLE workflow_task(id BIGINT PRIMARY KEY,module_instance_id BIGINT,node_id VARCHAR(64),"
                + "assignee_user_id BIGINT,candidate_role VARCHAR(64),status VARCHAR(32),round_no INT)");
        jdbc.execute("CREATE TABLE expert_review_batch(id BIGINT PRIMARY KEY,module_instance_id BIGINT,round_no INT)");
        jdbc.execute("CREATE TABLE expert_review_assignment(id BIGINT PRIMARY KEY,batch_id BIGINT,expert_user_id BIGINT,"
                + "valid BOOLEAN,review_status VARCHAR(32))");
        jdbc.update("INSERT INTO module_instance VALUES (1,100,'APPLICATION','OwnerTask',FALSE),(2,100,'APPLICATION','FinishedTask',TRUE)");
        jdbc.update("INSERT INTO workflow_task VALUES "
                + "(1,1,'OwnerTask',10,NULL,'OPEN',1),"
                + "(2,1,'OwnerTask',NULL,'FINANCE_ADMIN','OPEN',1),"
                + "(3,1,'OwnerTask',20,'EXPERT','OPEN',1),"
                + "(4,1,'OwnerTask',30,NULL,'COMPLETED',1),"
                + "(5,2,'FinishedTask',40,NULL,'OPEN',1),"
                + "(6,1,'OwnerTask',NULL,'EXPERT','OPEN',1)");
        jdbc.update("INSERT INTO expert_review_batch VALUES (10,1,1)");
        jdbc.update("INSERT INTO expert_review_assignment VALUES "
                + "(20,10,55,TRUE,'ASSIGNED'),"
                + "(21,10,56,FALSE,'ASSIGNED'),"
                + "(22,10,57,TRUE,'SUBMITTED')");
        ProjectAccessClient projects = mock(ProjectAccessClient.class);
        when(projects.decideBatch(anyString(), any())).thenAnswer(invocation -> {
            var request = invocation.<com.amatrix.sic.contracts.ProjectAccessBatchRequest>getArgument(1);
            return ApiResponse.ok(request.targets().stream().map(target -> new ProjectAccessResult(
                    target.decisionId(), target.projectId(), true, 1L, "ALLOWED", 10L, "Owner", 3L)).toList());
        });
        WorkflowInternalTokenProvider tokens = mock(WorkflowInternalTokenProvider.class);
        when(tokens.authorization()).thenReturn("Bearer service-token");
        policy = new WorkflowOperationPolicy(jdbc, projects, tokens);
    }

    @Test
    void exactCheckHonorsAssigneeCandidateRoleStatusAndAdmin() {
        assertThat(policy.exactCanOperate(actor(10L, List.of()), 1L)).isTrue();
        assertThat(policy.exactCanOperate(actor(99L, List.of("FINANCE_ADMIN")), 1L)).isTrue();
        assertThat(policy.exactCanOperate(actor(99L, List.of("EXPERT")), 1L)).isFalse();
        assertThat(policy.exactCanOperate(actor(55L, List.of("EXPERT")), 1L)).isTrue();
        assertThat(policy.exactCanOperate(actor(56L, List.of("EXPERT")), 1L)).isFalse();
        assertThat(policy.exactCanOperate(actor(57L, List.of("EXPERT")), 1L)).isFalse();
        assertThat(policy.exactCanOperate(actor(30L, List.of()), 1L)).isFalse();
        assertThat(policy.exactCanOperate(actor(99L, List.of("SYSTEM_ADMIN")), 1L)).isTrue();
        assertThat(policy.exactCanOperate(actor(99L, List.of("SYSTEM_ADMIN")), 2L)).isFalse();
        assertThat(policy.exactCanOperate(actor(40L, List.of()), 2L)).isFalse();
        assertThat(policy.exactCanOperate(null, 1L)).isFalse();
    }

    @Test
    void coarseAopCheckUsesAnImmutableActorContext() {
        assertThat(policy.canOperate(null, 1L)).isFalse();
        assertThat(policy.canOperate(actor(10L, List.of()), 1L)).isTrue();
    }

    private static ActorContext actor(Long userId, List<String> roles) {
        return new ActorContext(userId, "actor", null, null, roles, List.of(), "token", 1);
    }
}
