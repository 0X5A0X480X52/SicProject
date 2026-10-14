package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import com.zaxxer.hikari.*;
import com.amatrix.sic.contracts.*;
import com.amatrix.sic.messaging.*;
import com.amatrix.sic.messaging.dao.MessagingMapper;
import com.amatrix.sic.project.dao.ApplicationSagaMapper;
import com.amatrix.sic.project.client.WorkflowSagaClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;
import com.amatrix.sic.project.service.saga.ProjectApplicationSagaService;
import com.amatrix.sic.iam.dao.*;
import com.amatrix.sic.iam.bootstrap.BootstrapAdminInitializer;
import com.amatrix.sic.iam.config.properties.BootstrapAdminProperties;
import com.amatrix.sic.material.dao.MaterialMapper;
import com.amatrix.sic.material.policy.MaterialAccessPolicy;
import com.amatrix.sic.material.service.MaterialApplicationService;
import com.amatrix.sic.material.storage.MinioObjectStorage;
import tools.jackson.databind.ObjectMapper;

/** 用真实 MySQL 验证两个独立服务对象通过不同连接竞争，模拟副本间的数据库事务。 */
@Testcontainers
class MysqlReplicaSafetyTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sic_replicas").withUsername("sic").withPassword("isolated-replica-password");

    @Test void twoRecoveryWorkersConfirmOnceAndLateErrorsCannotRegressCompletedState() throws Exception {
        try(var source=database("project")) {
            var jdbc=new JdbcTemplate(source); var json=new ObjectMapper();
            var sessions=MysqlMapperIntegrationTest.template(source,"mapper/project/ApplicationSagaMapper.xml","mapper/messaging/MessagingMapper.xml");
            var mapper=sessions.getMapper(ApplicationSagaMapper.class);
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO project(id,project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status) VALUES(1,'P','Replica',7,'Owner','PROVISIONING')");
            jdbc.update("INSERT INTO application_saga(saga_id,project_id,actor_user_id,request_hash,status) VALUES(?,1,7,?,'WORKFLOW_CREATED')",id.toString(),"0".repeat(64));
            var transition=new StateTransitionContract(new StateRecordContract(1L,20L,1,1,"START",null,"DRAFT",null,"Node",null,null,null),"Node","DRAFT",false);
            var remote=mock(WorkflowSagaClient.class); var gate=new CountDownLatch(2);
            when(remote.status(anyString(),eq(id))).thenAnswer(call->{gate.countDown(); if(!gate.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Workers did not overlap");
                return ApiResponse.ok(new ApplicationSagaResult(id,1L,20L,transition,"CREATED"));});
            var tokens=mock(InternalTokenProvider.class);when(tokens.authorization()).thenReturn("Bearer test");
            var outbox=new OutboxService(new OutboxRepository(sessions.getMapper(MessagingMapper.class)),json);
            ProjectApplicationSagaService first=new ProjectApplicationSagaService(mapper,new TransactionTemplate(new DataSourceTransactionManager(source)),remote,tokens,outbox,json,null,null);
            ProjectApplicationSagaService second=new ProjectApplicationSagaService(mapper,new TransactionTemplate(new DataSourceTransactionManager(source)),remote,tokens,outbox,json,null,null);
            parallel(first::recoverIncompleteConfirmations,second::recoverIncompleteConfirmations);
            assertThat(jdbc.queryForObject("SELECT version FROM project WHERE id=1",Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT status FROM application_saga WHERE saga_id=?",String.class,id.toString())).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type='PROJECT_APPLICATION_STARTED'",Integer.class)).isEqualTo(1);
            assertThat(mapper.confirmationFailed(id.toString(),"late failure")).isZero();
            assertThat(mapper.retry(id.toString(),"late retry")).isZero();
            assertThat(mapper.workflowCreated(id.toString(),20L,"{}")).isZero();
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void failedConfirmationRollsBackActivationAndOutboxBeforeRecovery() throws Exception {
        try(var source=database("project")) {
            var jdbc=new JdbcTemplate(source); var json=new ObjectMapper();
            var sessions=MysqlMapperIntegrationTest.template(source,"mapper/project/ApplicationSagaMapper.xml","mapper/messaging/MessagingMapper.xml");
            var mapper=sessions.getMapper(ApplicationSagaMapper.class); UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO project(id,project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status) VALUES(1,'P','Rollback',7,'Owner','PROVISIONING')");
            jdbc.update("INSERT INTO application_saga(saga_id,project_id,actor_user_id,request_hash,status) VALUES(?,1,7,?,'WORKFLOW_CREATED')",id.toString(),"0".repeat(64));
            var remote=mock(WorkflowSagaClient.class); var tokens=mock(InternalTokenProvider.class);when(tokens.authorization()).thenReturn("Bearer test");
            when(remote.status(anyString(),eq(id))).thenReturn(ApiResponse.ok(new ApplicationSagaResult(id,1L,20L,null,"CREATED")));
            var outbox=spy(new OutboxService(new OutboxRepository(sessions.getMapper(MessagingMapper.class)),json));
            doThrow(new IllegalStateException("publication failed")).when(outbox).enqueue(anyString(),any());
            var service=new ProjectApplicationSagaService(mapper,new TransactionTemplate(new DataSourceTransactionManager(source)),remote,tokens,outbox,json,null,null);
            service.recoverIncompleteConfirmations();
            assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM project WHERE id=1",String.class)).isEqualTo("PROVISIONING");
            assertThat(jdbc.queryForObject("SELECT version FROM project WHERE id=1",Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM application_saga",String.class)).isEqualTo("WORKFLOW_CREATED");
            doCallRealMethod().when(outbox).enqueue(anyString(),any());
            jdbc.update("UPDATE application_saga SET next_retry_at=DATE_SUB(NOW(),INTERVAL 1 SECOND)");
            service.recoverIncompleteConfirmations();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM application_saga",String.class)).isEqualTo("COMPLETED");
        }
    }

    @Test void concurrentAdministratorBootstrapCreatesOneAccountWithoutResettingItsPassword() throws Exception {
        try(var source=database("iam")) {
            var jdbc=new JdbcTemplate(source);
            var mapper=MysqlMapperIntegrationTest.template(source,"mapper/iam/IdentityMapper.xml").getMapper(IdentityMapper.class);
            var repository=new IamRepository(mapper); var encoder=new BCryptPasswordEncoder();
            var initializer=new BootstrapAdminInitializer(new BootstrapAdminProperties("replica_admin","original-password","Admin"),repository,encoder);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            parallel(()->tx.executeWithoutResult(s->initializer.run(null)),()->tx.executeWithoutResult(s->initializer.run(null)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='replica_admin'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role",Integer.class)).isEqualTo(1);
            String hash=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='replica_admin'",String.class);
            var later=new BootstrapAdminInitializer(new BootstrapAdminProperties("replica_admin","changed-password","Changed"),repository,encoder);
            tx.executeWithoutResult(s->later.run(null));
            assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='replica_admin'",String.class)).isEqualTo(hash);
        }
    }

    @Test void concurrentUploadInstancesShareOneMaterialAndProduceUniqueVersions() throws Exception {
        try(var source=database("material")) {
            var jdbc=new JdbcTemplate(source);
            var sessions=MysqlMapperIntegrationTest.template(source,"mapper/material/MaterialMapper.xml","mapper/messaging/MessagingMapper.xml");
            var access=mock(MaterialAccessPolicy.class);when(access.canAccess(any(),eq(1L),anyString())).thenReturn(true);
            var storage=mock(MinioObjectStorage.class);
            var outbox=new OutboxService(new OutboxRepository(sessions.getMapper(MessagingMapper.class)),new ObjectMapper());
            var first=new MaterialApplicationService(sessions.getMapper(MaterialMapper.class),storage,access,outbox,new TransactionTemplate(new DataSourceTransactionManager(source)));
            var second=new MaterialApplicationService(sessions.getMapper(MaterialMapper.class),storage,access,outbox,new TransactionTemplate(new DataSourceTransactionManager(source)));
            var actor=new ActorContext(7L,"owner",1L,"Dept",List.of("PROJECT_LEADER"),List.of(),"jti",1);
            parallel(()->first.upload(actor,1L,"REPLICA_FILE",new MockMultipartFile("file","first.pdf","application/pdf",new byte[]{1})),
                     ()->second.upload(actor,1L,"REPLICA_FILE",new MockMultipartFile("file","second.pdf","application/pdf",new byte[]{2})));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT version_no FROM material_version ORDER BY version_no",Integer.class)).containsExactly(1,2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_version WHERE file_status='ACTIVE'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event",Integer.class)).isEqualTo(2);
        }
    }

    private static void parallel(Runnable first,Runnable second) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=executor.submit(()->{try{gate.await();first.run();}catch(InterruptedException e){throw new RuntimeException(e);}});
            var b=executor.submit(()->{try{gate.await();second.run();}catch(InterruptedException e){throw new RuntimeException(e);}});
            gate.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }
    }

    private static HikariDataSource database(String domain) throws Exception {
        String name="sic_replica_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        var config=new HikariConfig();config.setJdbcUrl(mysql.getJdbcUrl().replace("/sic_replicas","/"+name));
        config.setUsername("root");config.setPassword(mysql.getPassword());config.setMaximumPoolSize(4);config.setConnectionTimeout(5000);
        var source=new HikariDataSource(config);
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../..","infra/mysql/schema/"+domain+".sql")));
            if(domain.equals("iam"))ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../..","infra/mysql/seed/iam.sql")));
        }
        return source;
    }
}
