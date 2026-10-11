package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import com.zaxxer.hikari.*;
import com.amatrix.sic.contracts.*;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.project.client.IamDirectoryClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;
import com.amatrix.sic.project.dao.*;
import com.amatrix.sic.project.service.*;
import com.amatrix.sic.project.service.directory.ProjectUserDirectory;
import com.amatrix.sic.project.policy.ProjectAccessPolicy;
import com.amatrix.sic.workflow.dao.*;
import com.amatrix.sic.workflow.dao.review.ExpertReviewViewRepository;
import com.amatrix.sic.workflow.service.runtime.WorkflowWorkbenchQueryService;
import com.amatrix.sic.workflow.service.review.ExpertPersonalReviewService;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

@Testcontainers(disabledWithoutDocker=true)
class MysqlWorkbenchRegressionTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sic_ui").withUsername("sic").withPassword("isolated-test-password");

    @Test void departmentRepairIsAuditedCannotOverwriteAndRollsBackOnPublicationFailure() throws Exception {
        try(var source=database("project")) {
            var jdbc=new JdbcTemplate(source);project(jdbc);
            var sql=MysqlMapperIntegrationTest.template(source,"mapper/project/ProjectAuthorizationMapper.xml","mapper/project/ProjectAuditMapper.xml");
            var iam=mock(IamDirectoryClient.class);var tokens=mock(InternalTokenProvider.class);var outbox=mock(OutboxService.class);
            when(tokens.authorization()).thenReturn("Bearer test");
            when(iam.department("Bearer test",3L)).thenReturn(ApiResponse.ok(new DepartmentSummary(3L,"Research",true)));
            var service=new ProjectDepartmentService(iam,tokens,sql.getMapper(ProjectAuthorizationMapper.class),
                    sql.getMapper(ProjectAuditMapper.class),outbox,new TransactionTemplate(new DataSourceTransactionManager(source)));
            doThrow(new IllegalStateException("outbox unavailable")).when(outbox).enqueue(anyString(),any());
            assertThatThrownBy(() -> service.assignMissing(1L,actor("SYSTEM_ADMIN"),3L,"Verified historical ownership"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT dept_id FROM project WHERE id=1",Long.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log",Integer.class)).isZero();
            reset(outbox);
            service.assignMissing(1L,actor("SCIENCE_ADMIN"),3L,"Verified historical ownership");
            assertThat(jdbc.queryForObject("SELECT dept_id FROM project WHERE id=1",Long.class)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log",Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> service.assignMissing(1L,actor("SYSTEM_ADMIN"),3L,"Repeat")).hasMessageContaining("已有归属部门");
            assertThatThrownBy(() -> service.assignMissing(1L,actor("DEPT_ADMIN"),3L,"Cross boundary")).hasMessageContaining("全局管理员");
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void disabledOrMissingDepartmentIsRejectedWithoutStartingATransaction() throws Exception {
        var iam=mock(IamDirectoryClient.class);var tokens=mock(InternalTokenProvider.class);
        when(tokens.authorization()).thenReturn("Bearer test");
        when(iam.department("Bearer test",3L)).thenReturn(ApiResponse.ok(new DepartmentSummary(3L,"Research",false)));
        var transactions=mock(TransactionTemplate.class);
        var service=new ProjectDepartmentService(iam,tokens,mock(ProjectAuthorizationMapper.class),mock(ProjectAuditMapper.class),mock(OutboxService.class),transactions);
        assertThatThrownBy(() -> service.requireEnabled(null)).hasMessageContaining("分配部门");
        assertThatThrownBy(() -> service.assignMissing(1L,actor("SYSTEM_ADMIN"),3L,"Repair")).hasMessageContaining("已停用");
        verifyNoInteractions(transactions);
    }

    @Test @SuppressWarnings("unchecked")
    void finishedHistoryIsPagedAfterAuthorizationAndPreservesTheActorsOwnHistory() throws Exception {
        try(var source=database("workflow")) {
            var jdbc=new JdbcTemplate(source);
            for(int id=1;id<=130;id++) {
                jdbc.update("INSERT INTO project_snapshot(project_id,project_code,project_name,owner_user_id,lifecycle_stage) VALUES(?,?,?,7,'PROJECT_FINISHED')",id,"P-"+id,"Project "+id);
                jdbc.update("INSERT INTO module_instance(id,project_id,module_code,current_node_id,current_state,current_seq,finished,updated_at) VALUES(?,?,'APPLICATION','End','APPROVED',2,TRUE,'2030-01-01 00:00:00.000000')",id,id);
            }
            jdbc.update("INSERT INTO state_record(module_instance_id,seq,round_no,event_type,to_state,to_node_id,actor_user_id,actor_name_snapshot) VALUES(1,2,1,'APPROVE','APPROVED','End',7,'User')");
            var runtime=MysqlMapperIntegrationTest.template(source,"mapper/workflow/WorkflowRuntimeMapper.xml").getMapper(WorkflowRuntimeMapper.class);
            var operations=mock(WorkflowOperationPolicy.class);when(operations.operableModuleIds(any(),anyList())).thenReturn(Set.of());
            var projects=mock(ProjectAccessClient.class);var tokens=mock(WorkflowInternalTokenProvider.class);
            when(tokens.authorization()).thenReturn("Bearer test");
            when(projects.decideBatch(anyString(),any())).thenAnswer(invocation -> {
                ProjectAccessBatchRequest request=invocation.getArgument(1);
                assertThat(request.targets()).hasSizeLessThanOrEqualTo(100);
                return ApiResponse.ok(request.targets().stream().map(target -> new ProjectAccessResult(target.decisionId(),target.projectId(),
                        target.projectId()<=3,1,"TEST",7L,"Owner",3L)).toList());
            });
            var service=new WorkflowWorkbenchQueryService(runtime,operations,projects,tokens);
            var first=service.query(actor("PROJECT_LEADER"),"FINISHED",null,null,1,2);
            assertThat(first.get("hasMore")).isEqualTo(true);
            assertThat((List<Map<String,Object>>)first.get("items")).extracting(row -> row.get("moduleInstanceId")).containsExactly(3L,2L);
            var second=service.query(actor("PROJECT_LEADER"),"FINISHED",null,null,2,2);
            assertThat((List<Map<String,Object>>)second.get("items")).extracting(row -> row.get("moduleInstanceId")).containsExactly(1L);
            assertThat(second.get("hasMore")).isEqualTo(false);
            var operated=service.query(actor("PROJECT_LEADER"),"OPERATED",null,null,1,10);
            assertThat((List<Map<String,Object>>)operated.get("items")).singleElement().satisfies(row -> {
                assertThat(row.get("operated")).isEqualTo(true); assertThat(row.get("canOperate")).isEqualTo(false);
            });
            assertThat(runtime.workbench()).isEmpty();
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test @SuppressWarnings("unchecked")
    void ownReviewHistorySurvivesNodeClosureWithoutExposingAnotherExpertsScores() throws Exception {
        try(var source=database("workflow")) {
            var jdbc=new JdbcTemplate(source);
            jdbc.update("INSERT INTO project_snapshot(project_id,project_code,project_name,owner_user_id) VALUES(1,'P-1','Project',9)");
            jdbc.update("INSERT INTO module_instance(id,project_id,module_code,current_node_id,current_state,current_seq) VALUES(1,1,'APPLICATION','DeptExpertReviewTask','REVIEW',3)");
            jdbc.update("INSERT INTO workflow_node_config(id,workflow_definition_id,node_id,node_name,node_type,material_requirements_json,document_configs_json) VALUES(1,1,'DeptExpertAssignTask','Assign','USER_TASK','[]','[]')");
            jdbc.update("INSERT INTO expert_review_batch(id,module_instance_id,workflow_node_id,round_no,review_type,review_title,created_by,final_score,highest_score) VALUES(1,1,1,1,'APPLICATION','Review',9,90,95)");
            jdbc.update("INSERT INTO expert_review_assignment(id,batch_id,expert_user_id,review_status,total_score,review_comment,submitted_at) VALUES(1,1,7,'SUBMITTED',85,'Own comment',NOW(6)),(2,1,8,'ASSIGNED',95,'Other private comment',NULL)");
            jdbc.update("INSERT INTO expert_review_score(assignment_id,item_code,item_name,score_value) VALUES(1,'OVERALL','Overall',85),(2,'OVERALL','Overall',95)");
            var mapper=MysqlMapperIntegrationTest.template(source,"mapper/workflow/ExpertReviewViewMapper.xml").getMapper(ExpertReviewViewMapper.class);
            var operations=mock(WorkflowOperationPolicy.class);
            var service=new ExpertPersonalReviewService(mapper,operations);
            var waiting=service.detail(actor("EXPERT"),1L);
            assertThat(waiting.get("status")).isEqualTo("SUBMITTED");assertThat(waiting.get("canReview")).isEqualTo(false);
            assertThatThrownBy(() -> service.detail(actor("EXPERT"),2L)).hasMessageContaining("不属于当前账号");
            jdbc.update("UPDATE module_instance SET current_node_id='DeptExpertSummaryTask',current_state='SUMMARY' WHERE id=1");
            jdbc.update("UPDATE expert_review_batch SET access_ended_at=NOW(6),status='COMPLETED' WHERE id=1");
            var ended=service.detail(actor("PROJECT_LEADER"),1L);
            assertThat(ended.get("status")).isEqualTo("COMPLETED");assertThat(ended.get("canReview")).isEqualTo(false);
            assertThat((List<Map<String,Object>>)ended.get("scores")).singleElement().satisfies(score -> assertThat(score.get("scoreValue").toString()).startsWith("85"));
            var personal=new ExpertReviewViewRepository(mapper).personalBatchDetail(1L,7L);
            assertThat((Map<String,Object>)personal.get("batch")).doesNotContainKeys("finalScore","highestScore","lowestScore","finalResult");
            assertThat((List<Map<String,Object>>)personal.get("assignments")).hasSize(1);
            assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(personal)).doesNotContain("Other private comment");
            verifyNoInteractions(operations); // Closed records do not attempt to re-open project permissions.
        }
    }

    @Test void lateFinanceRoleAndProjectGrantBothMatterAndScopeMismatchesRemainDenied() throws Exception {
        try(var source=database("project")) {
            var jdbc=new JdbcTemplate(source);project(jdbc);
            var template=MysqlMapperIntegrationTest.template(source,"mapper/project/ProjectAuthorizationMapper.xml","mapper/project/ProjectScopeMapper.xml");
            var grants=template.getMapper(ProjectAuthorizationMapper.class);
            var users=mock(ProjectUserDirectory.class);
            var readiness=new ProjectFinanceReadinessService(grants,users);
            assertThat(readiness.readiness(1L,"ACCEPTANCE",1,"FinancialAcceptanceTask").get("ready")).isEqualTo(false);
            jdbc.update("INSERT INTO project_access_grant(project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,module_type,round_no,task_node_id,granted_by,granted_by_name_snapshot) VALUES(1,7,'Finance','PROJECT_FINANCE_HANDLER_ASSIGNMENT','ACCEPTANCE',1,'FinancialAcceptanceTask',9,'Admin')");
            when(users.fetchAll(List.of(7L))).thenReturn(List.of(new UserSummary(7L,"finance","Finance",3L,"Research",List.of(),true,1)));
            assertThat(readiness.readiness(1L,"ACCEPTANCE",1,"FinancialAcceptanceTask").get("ready")).isEqualTo(false);
            when(users.fetchAll(List.of(7L))).thenReturn(List.of(new UserSummary(7L,"finance","Finance",3L,"Research",List.of("FINANCE_ADMIN"),true,2)));
            assertThat(readiness.readiness(1L,"ACCEPTANCE",1,"FinancialAcceptanceTask").get("ready")).isEqualTo(true);
            var policy=new ProjectAccessPolicy(template.getMapper(ProjectScopeMapper.class));
            assertThat(policy.decideBatch(actor("FINANCE_ADMIN"),List.of(new ProjectOperationTarget("task",1L,"OPERATE_FINANCE_TASK","ACCEPTANCE",1,"FinancialAcceptanceTask"))).getFirst().allowed()).isTrue();
            assertThat(policy.decideBatch(actor("FINANCE_ADMIN"),List.of(new ProjectOperationTarget("task",1L,"OPERATE_FINANCE_TASK","ACCEPTANCE",2,"FinancialAcceptanceTask"))).getFirst().allowed()).isFalse();
            assertThat(readiness.readiness(1L,"CONTRACT",1,"FinancialAcceptanceTask").get("ready")).isEqualTo(false);
            jdbc.update("UPDATE project_access_grant SET effective_to=DATE_SUB(NOW(6),INTERVAL 1 SECOND)");
            assertThat(readiness.readiness(1L,"ACCEPTANCE",1,"FinancialAcceptanceTask").get("ready")).isEqualTo(false);
        }
    }

    private static ActorContext actor(String role) {return new ActorContext(7L,"User",3L,"Research",List.of(role),List.of(),"test",1);}
    private static void project(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO project(id,project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status) VALUES(1,'P-1','Project',9,'Owner','ACTIVE')");
    }
    private static HikariDataSource database(String domain) throws Exception {
        String name="sic_ui_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        var config=new HikariConfig();config.setJdbcUrl(mysql.getJdbcUrl().replace("/sic_ui","/"+name));
        config.setUsername("root");config.setPassword(mysql.getPassword());config.setMaximumPoolSize(1);config.setConnectionTimeout(1000);
        var source=new HikariDataSource(config);
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../..","infra/mysql/schema/"+domain+".sql")));
        }
        return source;
    }
}
