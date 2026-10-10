package com.amatrix.sic.integration;

import com.amatrix.sic.project.client.IamDirectoryClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;
import com.amatrix.sic.project.service.directory.ProjectUserDirectory;
import com.amatrix.sic.project.service.ProjectAuthorizationService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.zaxxer.hikari.*;
import com.amatrix.sic.messaging.*;
import com.amatrix.sic.messaging.dao.MessagingMapper;
import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.dao.IdentityMapper;
import com.amatrix.sic.iam.dao.AdminMapper;
import com.amatrix.sic.iam.dao.QualificationMapper;
import com.amatrix.sic.iam.dao.OverviewMapper;
import com.amatrix.sic.project.*;
import com.amatrix.sic.contracts.*;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker=true)
class MysqlMapperIntegrationTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sic_mappers").withUsername("sic").withPassword("mapper-test-password");

    @Test void identityMapperPreservesGeneratedKeysNullsRolesAndMinimalBatchProjection() throws Exception {
        try(var source=database("iam")) {
            var template=template(source,"mapper/iam/IdentityMapper.xml");
            var repository=new IamRepository(template.getMapper(IdentityMapper.class));
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            Long id=transactions.execute(status -> {
                long user=repository.insertUser("mapper-user","fixture-password-hash","User",null,null,null);
                repository.assignRole(user,"EXPERT"); repository.assignRole(user,"EXPERT");
                return user;
            });
            var account=repository.findById(id);
            assertThat(account.userId()).isEqualTo(id);
            assertThat(account.passwordHash()).isEqualTo("fixture-password-hash");
            assertThat(account.deptId()).isNull();
            assertThat(account.phone()).isNull();
            assertThat(account.enabled()).isTrue();
            assertThat(account.roleCodes()).containsExactly("EXPERT");
            assertThat(repository.findByUsername("mapper-user").userId()).isEqualTo(id);
            var summaries=transactions.execute(status -> repository.summaries(List.of(id,id,999999L)));
            assertThat(summaries).hasSize(1);
            assertThat(summaries.getFirst().roleCodes()).containsExactly("EXPERT");
            assertThat(new ObjectMapper().writeValueAsString(summaries)).doesNotContain("password","permissionCodes");
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void inboxAndOutboxRollbackTogetherAndLeaseOwnerFencesStalePublishers() throws Exception {
        try(var source=database("notification")) {
            var mapper=template(source,"mapper/messaging/MessagingMapper.xml").getMapper(MessagingMapper.class);
            var outbox=new OutboxRepository(mapper);
            var guard=new ConsumedEventGuard(mapper);
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            var jdbc=new JdbcTemplate(source);
            UUID id=UUID.randomUUID();
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                assertThat(guard.begin(id,"test")).isTrue(); outbox.insert(id,"TEST","test.route","{}");
                throw new IllegalStateException("Injected business rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(mapper.pending()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumed_event",Long.class)).isZero();
            transactions.executeWithoutResult(status -> {
                assertThat(guard.begin(id,"test")).isTrue(); outbox.insert(id,"TEST","test.route","{}");
            });
            assertThat(guard.begin(id,"test")).isFalse();
            try(var workers=Executors.newFixedThreadPool(2)) {
                var first=workers.submit(() -> outbox.claimPending(10,"first",Duration.ofMinutes(1)));
                var second=workers.submit(() -> outbox.claimPending(10,"second",Duration.ofMinutes(1)));
                assertThat(first.get(10,TimeUnit.SECONDS).size()+second.get(10,TimeUnit.SECONDS).size()).isEqualTo(1);
            }
            String owner=jdbc.queryForObject("SELECT lock_owner FROM outbox_event",String.class);
            outbox.markPublished(id,"unrelated-owner"); assertThat(mapper.pending()).isEqualTo(1);
            jdbc.update("UPDATE outbox_event SET locked_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND");
            assertThat(outbox.claimPending(10,"replacement",Duration.ofMinutes(1))).hasSize(1);
            outbox.markFailed(id,owner,"obsolete failure",Instant.now());
            outbox.markPublished(id,owner); assertThat(mapper.pending()).isEqualTo(1);
            outbox.markPublished(id,"replacement"); assertThat(mapper.pending()).isZero();
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void iamAdministrationAndQualificationKeepNullFieldsAndTransactionalStateGuards() throws Exception {
        try(var source=database("iam")) {
            var sessions=template(source,"mapper/iam/IdentityMapper.xml","mapper/iam/AdminMapper.xml",
                    "mapper/iam/QualificationMapper.xml","mapper/iam/OverviewMapper.xml");
            var admin=sessions.getMapper(AdminMapper.class);
            var qualifications=sessions.getMapper(QualificationMapper.class);
            var users=new IamRepository(sessions.getMapper(IdentityMapper.class));
            long id=users.insertUser("null-fields","fixture","Null User",null,null,null);
            assertThat(admin.users(java.util.Map.of("username","%fields%"))).extracting(row -> row.userId()).containsExactly(id);
            assertThat(admin.user(id).roleCodes()).isNull();
            assertThat(admin.departments(null).getFirst()).containsKey("parentDeptId");
            assertThat(sessions.getMapper(OverviewMapper.class).counters().users()).isEqualTo(1);
            assertThat(sessions.getMapper(OverviewMapper.class).roleCounts()).isNotEmpty();
            var insert=new java.util.LinkedHashMap<String,Object>();
            insert.put("userId",id);insert.put("departmentId",null);insert.put("specialty","fixture");
            insert.put("professionalTitle",null);insert.put("reason","fixture");qualifications.insert(insert);
            long application=((Number)insert.get("id")).longValue();
            assertThat(qualifications.applications(java.util.Map.of("userId",id))).hasSize(1);
            assertThat(qualifications.departmentReview(application,id,"Approved",null,"PENDING_SCIENCE_REVIEW")).isEqualTo(1);
            assertThat(qualifications.departmentReview(application,id,"Duplicate",null,"PENDING_SCIENCE_REVIEW")).isZero();
            assertThat(qualifications.scienceReview(application,id,"Approved",null,"APPROVED")).isEqualTo(1);
            assertThat(qualifications.scienceReview(application,id,"Duplicate",null,"APPROVED")).isZero();
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                admin.lockAllRoles();admin.lockedVersions(List.of(id));admin.assignRole(id,"EXPERT");
                admin.incrementVersions(List.of(id));throw new IllegalStateException("Rollback role mutation");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(admin.authVersion(id)).isEqualTo(1);
            assertThat(admin.roleCodes(id)).isEmpty();
        }
    }

    @Test void rolePermissionChangesSerializeWithNewRoleBindings() throws Exception {
        try(var source=database("iam")) {
            source.setMaximumPoolSize(2);
            var sessions=template(source,"mapper/iam/IdentityMapper.xml","mapper/iam/AdminMapper.xml");
            var users=new IamRepository(sessions.getMapper(IdentityMapper.class));
            var admin=sessions.getMapper(AdminMapper.class);
            long id=users.insertUser("new-expert","fixture","Expert",null,null,null);
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            try(var workers=Executors.newSingleThreadExecutor()) {
                var future=transactions.execute(status -> {
                    Long role=admin.roleId("EXPERT");admin.deleteRolePermissions(role);
                    var started=new java.util.concurrent.CountDownLatch(1);
                    var assignment=workers.submit(() -> transactions.execute(inner -> {
                        started.countDown();users.assignRole(id,"EXPERT");return users.findById(id);
                    }));
                    try {
                        assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();
                        assertThatThrownBy(() -> assignment.get(200,TimeUnit.MILLISECONDS))
                                .isInstanceOf(java.util.concurrent.TimeoutException.class);
                    } catch(InterruptedException ex) { throw new RuntimeException(ex); }
                    admin.assignPermission(role,"project:view");return assignment;
                });
                var account=future.get(5,TimeUnit.SECONDS);
                assertThat(account.roleCodes()).containsExactly("EXPERT");
                assertThat(account.permissionCodes()).containsExactly("project:view");
            }
        }
    }

    @Test void projectAuthorizationDetailNeedsOnlyOnePoolConnection() throws Exception {
        try(var source=database("project")) {
            var jdbc=new JdbcTemplate(source);
            var identities=mock(IamDirectoryClient.class);
            var users=new ProjectUserDirectory(template(source,"mapper/project/UserSnapshotMapper.xml").getMapper(com.amatrix.sic.project.dao.UserSnapshotMapper.class),identities,mock(InternalTokenProvider.class),new ObjectMapper());
            users.upsert(new UserSummary(1L,"owner","Owner",10L,"Dept",List.of("PROJECT_LEADER"),true,1));
            users.upsert(new UserSummary(2L,"expert","Expert",10L,"Dept",List.of("EXPERT"),true,1));
            jdbc.update("INSERT INTO project(project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status) VALUES('ONE','One',1,'Owner','ACTIVE')");
            jdbc.update("INSERT INTO project_member(project_id,user_id,member_role,user_name_snapshot) VALUES(1,2,'MEMBER','Expert')");
            jdbc.update("INSERT INTO project_access_grant(project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,granted_by,granted_by_name_snapshot) VALUES(1,2,'Expert','PROJECT_MODULE_EXPERT_ASSIGNMENT',1,'Owner')");
            var authorization=template(source,"mapper/project/ProjectAuthorizationMapper.xml","mapper/project/ProjectAuditMapper.xml");
            var service=new ProjectAuthorizationService(authorization.getMapper(com.amatrix.sic.project.dao.ProjectAuthorizationMapper.class),authorization.getMapper(com.amatrix.sic.project.dao.ProjectAuditMapper.class),users,mock(OutboxService.class),new ObjectMapper());
            var actor=new ActorContext(1L,"admin",10L,"Dept",List.of("SYSTEM_ADMIN"),List.of(),"one-connection",1);
            var detail=service.detail(1L,actor);
            assertThat((List<?>)detail.get("members")).hasSize(1);
            assertThat((List<?>)detail.get("expertGrants")).hasSize(1);
            assertThat(((java.util.Map<?,?>)detail.get("leader")).get("userId")).isEqualTo(1L);
            verifyNoInteractions(identities);
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void explicitWorkflowAdoptionAddsOnlyMissingCodesAndPreservesInstalledNodeIds() throws Exception {
        try(var source=database("workflow")) {
            Path seed=Path.of("../../infra/mysql/seed/workflow-missing-assets.sql");
            try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new FileSystemResource(seed)); }
            var jdbc=new JdbcTemplate(source);
            Long application=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE code='APPLICATION'",Long.class);
            Long node=jdbc.queryForObject("SELECT MIN(id) FROM workflow_node_config WHERE workflow_definition_id=?",Long.class,application);
            jdbc.update("UPDATE workflow_definition SET version=77,published=FALSE,name='Installed legacy definition' WHERE id=?",application);
            jdbc.update("UPDATE workflow_node_config SET node_name='Installed custom node' WHERE id=?",node);
            jdbc.update("DELETE FROM workflow_node_config WHERE workflow_definition_id IN (SELECT id FROM workflow_definition WHERE code='CONTRACT')");
            jdbc.update("DELETE FROM workflow_transition_config WHERE workflow_definition_id IN (SELECT id FROM workflow_definition WHERE code='CONTRACT')");
            jdbc.update("DELETE FROM workflow_definition WHERE code='CONTRACT'");
            try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new FileSystemResource(seed)); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_definition",Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT name FROM workflow_definition WHERE id=?",String.class,application)).isEqualTo("Installed legacy definition");
            assertThat(jdbc.queryForObject("SELECT node_name FROM workflow_node_config WHERE id=?",String.class,node)).isEqualTo("Installed custom node");
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM workflow_node_config",Long.class);
            try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new FileSystemResource(seed)); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_node_config",Long.class)).isEqualTo(count);
        }
    }

    @Test void materialMappersPreserveUploadContextReservationAndGeneratedDocumentIdempotency() throws Exception {
        try(var source=database("material")) {
            var sessions=template(source,"mapper/material/MaterialMapper.xml","mapper/material/MaterialReservationMapper.xml",
                    "mapper/material/GeneratedDocumentMapper.xml");
            var materials=sessions.getMapper(com.amatrix.sic.material.dao.MaterialMapper.class);
            var storage=mock(com.amatrix.sic.material.storage.MinioObjectStorage.class);
            var access=mock(com.amatrix.sic.material.policy.MaterialAccessPolicy.class);
            when(access.canAccess(any(),anyLong(),anyString())).thenReturn(true);
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            var service=new com.amatrix.sic.material.service.MaterialApplicationService(materials,storage,access,mock(OutboxService.class),transactions);
            var actor=new ActorContext(7L,"owner",null,null,List.of("PROJECT_LEADER"),List.of(),"material-test",1);
            var uploaded=service.upload(actor,10L,"FORM",new org.springframework.mock.web.MockMultipartFile("file","blank.pdf","application/pdf",new byte[]{1,2,3}));
            var context=(java.util.Map<?,?>)uploaded.get("context");
            Long version=(Long)context.get("materialVersionId");
            assertThat(context.get("uploadedAt")).isInstanceOf(java.time.LocalDateTime.class);
            assertThat(context.containsKey("uploadedByName")).isTrue();
            assertThat(context.get("isCurrent")).isEqualTo(true);
            var reservations=new com.amatrix.sic.material.service.reservation.MaterialReservationService(sessions.getMapper(com.amatrix.sic.material.dao.MaterialReservationMapper.class));
            var command=new MaterialReservationCommand("mysql-reservation",7L,10L,20L,0,List.of(version),List.of("FORM"));
            var reservation=transactions.execute(status -> reservations.reserve(command));
            assertThat(transactions.execute(status -> reservations.reserve(command)).reservationId()).isEqualTo(reservation.reservationId());
            transactions.executeWithoutResult(status -> reservations.confirm(reservation.reservationId()));
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> service.delete(actor,version)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            var renderer=mock(com.amatrix.sic.material.service.document.GeneratedDocumentRenderer.class);
            when(renderer.render(any())).thenReturn(new byte[]{4,5,6});
            var documents=new com.amatrix.sic.material.service.document.GeneratedDocumentService(materials,
                    sessions.getMapper(com.amatrix.sic.material.dao.GeneratedDocumentMapper.class),storage,renderer,new ObjectMapper());
            var generation=new DocumentGenerationCommand("document-once",10L,20L,30L,1,"APPLICATION","Node","State",
                    "TEST_DOCUMENT","Document",null,null,null,"GENERATED_FORM","Generated",true,7L,java.util.Map.of("projectId",10));
            transactions.executeWithoutResult(status -> { documents.generate(generation);documents.generate(generation); });
            var jdbc=new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_document",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT material_version_id FROM generated_document",Long.class)).isNotNull();
            assertThat(service.list(actor,10L)).hasSize(2);
            verify(storage,times(2)).put(anyString(),any(java.io.InputStream.class),anyLong(),anyString());
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void concurrentExpertScoresKeepBothSubmissionsInTheFinalAggregate() throws Exception {
        try(var source=database("workflow")) {
            source.setMaximumPoolSize(3);
            try(var connection=source.getConnection()) {
                ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../infra/mysql/seed/workflow-assets.sql")));
            }
            var jdbc=new JdbcTemplate(source);
            Long definition=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE code='APPLICATION'",Long.class);
            Long node=jdbc.queryForObject("SELECT id FROM workflow_node_config WHERE workflow_definition_id=? AND node_id='DeptExpertAssignTask'",Long.class,definition);
            jdbc.update("INSERT INTO module_instance(id,project_id,module_code,definition_id,current_node_id,current_state,current_seq,current_round,finished) VALUES(100,10,'APPLICATION',?,'DeptExpertReviewTask','APPLICATION_DEPT_EXPERT_REVIEWING',1,1,FALSE)",definition);
            jdbc.update("INSERT INTO expert_review_batch(id,module_instance_id,workflow_node_id,round_no,review_type,review_title,min_expert_count,expected_expert_count,pass_score,recommend_score,status,created_by) VALUES(200,100,?,1,'APPLICATION','Concurrent review',2,2,70,85,'IN_PROGRESS',7)",node);
            jdbc.update("INSERT INTO expert_review_assignment(id,batch_id,expert_user_id,expert_name_snapshot) VALUES(300,200,8,'First expert'),(301,200,9,'Second expert')");
            var sessions=template(source,"mapper/workflow/ExpertReviewMapper.xml","mapper/workflow/ExpertReviewViewMapper.xml");
            var delegate=sessions.getMapper(com.amatrix.sic.workflow.dao.ExpertReviewMapper.class);
            var guarded=mock(com.amatrix.sic.workflow.dao.ExpertReviewMapper.class,org.mockito.AdditionalAnswers.delegatesTo(delegate));
            var observed=new java.util.concurrent.CountDownLatch(2);
            when(guarded.batch(200L,false)).thenAnswer(call -> {
                var row=delegate.batch(200L,false);
                observed.countDown();
                if(!observed.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Concurrent reads did not rendezvous");
                return row;
            });
            var policy=mock(com.amatrix.sic.workflow.policy.WorkflowOperationPolicy.class);
            when(policy.exactCanOperate(any(),anyLong())).thenReturn(true);
            var outbox=mock(OutboxService.class);
            var manager=new DataSourceTransactionManager(source);
            var target=new com.amatrix.sic.workflow.service.review.ExpertReviewService(guarded,policy,outbox,
                    mock(com.amatrix.sic.workflow.client.WorkflowServiceTokenClient.class),
                    mock(com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider.class),
                    new com.amatrix.sic.workflow.dao.review.ExpertReviewViewRepository(sessions.getMapper(com.amatrix.sic.workflow.dao.ExpertReviewViewMapper.class)),
                    mock(com.amatrix.sic.workflow.client.ProjectAccessClient.class),manager);
            var interceptor=new org.springframework.transaction.interceptor.TransactionInterceptor();
            interceptor.setTransactionManager(manager);
            interceptor.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
            interceptor.afterPropertiesSet();
            var factory=new org.springframework.aop.framework.ProxyFactory(target);
            factory.setProxyTargetClass(true);factory.addAdvice(interceptor);
            var service=(com.amatrix.sic.workflow.service.review.ExpertReviewService)factory.getProxy();
            try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for(int index=0;index<2;index++) {
                    final int current=index;
                    futures.add(workers.submit(() -> service.submit(
                            new ActorContext(8L+current,"expert",null,null,List.of("EXPERT"),List.of(),"concurrent-review",1),300L+current,
                            new com.amatrix.sic.workflow.dto.ExpertReviewDtos.SubmitScoreRequest(false,true,"APPROVED","Concurrent test",
                                    List.of(new com.amatrix.sic.workflow.dto.ExpertReviewDtos.ScoreItem("TOTAL","Total",java.math.BigDecimal.ONE,
                                            new java.math.BigDecimal("100"),new java.math.BigDecimal(current==0?"80":"90"),"Score"))))));
                }
                for(var future:futures)future.get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(jdbc.queryForObject("SELECT submitted_expert_count FROM expert_review_batch WHERE id=200",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT final_score FROM expert_review_batch WHERE id=200",java.math.BigDecimal.class)).isEqualByComparingTo("85");
            assertThat(jdbc.queryForObject("SELECT status FROM expert_review_batch WHERE id=200",String.class)).isEqualTo("COMPLETED");
            verify(outbox,times(1)).enqueue(eq("workflow.expert-review-completed"),any());
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void lateMaterialConfirmationAndDeletionSerializeOnTheVersion() throws Exception {
        try(var source=database("material")) {
            source.setMaximumPoolSize(2);
            var jdbc=new JdbcTemplate(source);
            jdbc.update("INSERT INTO material(id,project_id,material_type_code,title,created_by) VALUES(10,20,'FORM','Evidence',7)");
            jdbc.update("INSERT INTO material_version(id,material_id,version_no,original_filename,content_type,size_bytes,sha256,object_key,file_status,uploaded_by) VALUES(30,10,1,'blank.pdf','application/pdf',3,REPEAT('a',64),'concurrent-evidence','ACTIVE',7)");
            var sessions=template(source,"mapper/material/MaterialMapper.xml","mapper/material/MaterialReservationMapper.xml");
            var reservations=sessions.getMapper(com.amatrix.sic.material.dao.MaterialReservationMapper.class);
            var manager=new DataSourceTransactionManager(source);
            var service=transactional(new com.amatrix.sic.material.service.reservation.MaterialReservationService(reservations),manager);
            var command=new MaterialReservationCommand("concurrent-evidence",7L,20L,40L,1,List.of(30L),List.of("FORM"));
            String reservation=service.reserve(command).reservationId();
            jdbc.update("UPDATE material_reservation SET status='EXPIRED' WHERE reservation_id=?",reservation);
            var locked=new java.util.concurrent.CountDownLatch(1);
            var permissionChecked=new java.util.concurrent.CountDownLatch(1);
            var release=new java.util.concurrent.CountDownLatch(1);
            var guarded=mock(com.amatrix.sic.material.dao.MaterialReservationMapper.class,org.mockito.AdditionalAnswers.delegatesTo(reservations));
            doAnswer(call -> {
                var rows=reservations.available(call.getArgument(0),true);
                locked.countDown();
                if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Confirmation lock was not released");
                return rows;
            }).when(guarded).available(anyList(),eq(true));
            var confirming=transactional(new com.amatrix.sic.material.service.reservation.MaterialReservationService(guarded),manager);
            var access=mock(com.amatrix.sic.material.policy.MaterialAccessPolicy.class);
            when(access.canAccess(any(),anyLong(),anyString())).thenAnswer(call -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                permissionChecked.countDown();return true;
            });
            var storage=mock(com.amatrix.sic.material.storage.MinioObjectStorage.class);
            var files=new com.amatrix.sic.material.service.MaterialApplicationService(sessions.getMapper(com.amatrix.sic.material.dao.MaterialMapper.class),
                    storage,access,mock(OutboxService.class),new TransactionTemplate(manager));
            try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var confirmation=workers.submit(() -> confirming.confirm(reservation));
                assertThat(locked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var deletion=workers.submit(() -> {
                    try { files.delete(new ActorContext(7L,"owner",null,null,List.of("PROJECT_LEADER"),List.of(),"delete-test",1),30L);return 200; }
                    catch(org.springframework.web.server.ResponseStatusException ex) { return ex.getStatusCode().value(); }
                });
                assertThat(permissionChecked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                release.countDown();
                confirmation.get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(deletion.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
            } finally { release.countDown(); }
            assertThat(jdbc.queryForObject("SELECT status FROM material_reservation WHERE reservation_id=?",String.class,reservation)).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT file_status FROM material_version WHERE id=30",String.class)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM object_deletion_job",Integer.class)).isZero();
            verify(storage,never()).remove(anyString());
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void memberBatchesOnDifferentProjectsDoNotDeadlockAtThePrimaryIndexEnd() throws Exception {
        try(var source=database("project")) {
            source.setMaximumPoolSize(2);
            var jdbc=new JdbcTemplate(source);
            jdbc.update("INSERT INTO project(id,project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status,business_status) VALUES(10,'LOCK-10','First',7,'Owner','ACTIVE','APPLICATION'),(20,'LOCK-20','Second',7,'Owner','ACTIVE','APPLICATION')");
            jdbc.update("INSERT INTO project_member(project_id,user_id,member_role,user_name_snapshot) VALUES(10,7,'LEADER','Owner'),(20,7,'LEADER','Owner')");
            var sessions=template(source,"mapper/project/ProjectAuthorizationMapper.xml","mapper/project/UserSnapshotMapper.xml");
            var actual=sessions.getMapper(com.amatrix.sic.project.dao.ProjectAuthorizationMapper.class);
            var guarded=mock(com.amatrix.sic.project.dao.ProjectAuthorizationMapper.class,org.mockito.AdditionalAnswers.delegatesTo(actual));
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            doAnswer(call -> {
                java.util.Map<String,Object> row=call.getArgument(0);
                int changed=actual.upsertMember(row);
                if(row.get("userId").equals(8L))barrier.await(5,TimeUnit.SECONDS);
                return changed;
            }).when(guarded).upsertMember(anyMap());
            var users=new ProjectUserDirectory(sessions.getMapper(com.amatrix.sic.project.dao.UserSnapshotMapper.class),
                    mock(IamDirectoryClient.class),mock(InternalTokenProvider.class),new ObjectMapper());
            for(long id=7;id<=9;id++)users.upsert(new UserSummary(id,"lock-user-"+id,"User "+id,null,null,List.of("PROJECT_LEADER"),true,1));
            var service=transactional(new ProjectAuthorizationService(guarded,mock(com.amatrix.sic.project.dao.ProjectAuditMapper.class),
                    users,mock(OutboxService.class),new ObjectMapper()),new DataSourceTransactionManager(source));
            var actor=new ActorContext(7L,"owner",null,null,List.of("PROJECT_LEADER"),List.of(),"locks",1);
            try(var workers=Executors.newFixedThreadPool(2)) {
                var first=workers.submit(() -> service.upsertMembers(10L,actor,List.of(8L,9L),"Concurrent insert"));
                var second=workers.submit(() -> service.upsertMembers(20L,actor,List.of(8L,9L),"Concurrent insert"));
                assertThat(first.get(10,TimeUnit.SECONDS).get("affectedCount")).isEqualTo(2);
                assertThat(second.get(10,TimeUnit.SECONDS).get("affectedCount")).isEqualTo(2);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_member",Integer.class)).isEqualTo(6);
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void concurrentExpertGrantsSerializeOnTheProjectBeforeCheckingDuplicates() throws Exception {
        try(var source=database("project")) {
            source.setMaximumPoolSize(2);
            var jdbc=new JdbcTemplate(source);
            jdbc.update("INSERT INTO project(id,project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status,business_status) VALUES(10,'GRANT-10','Project',7,'Owner','ACTIVE','APPLICATION')");
            var sessions=template(source,"mapper/project/ProjectAuthorizationMapper.xml","mapper/project/UserSnapshotMapper.xml");
            var users=new ProjectUserDirectory(sessions.getMapper(com.amatrix.sic.project.dao.UserSnapshotMapper.class),
                    mock(IamDirectoryClient.class),mock(InternalTokenProvider.class),new ObjectMapper());
            users.upsert(new UserSummary(8L,"grant-expert","Expert",null,null,List.of("EXPERT"),true,1));
            var service=transactional(new ProjectAuthorizationService(sessions.getMapper(com.amatrix.sic.project.dao.ProjectAuthorizationMapper.class),
                    mock(com.amatrix.sic.project.dao.ProjectAuditMapper.class),users,mock(OutboxService.class),new ObjectMapper()),new DataSourceTransactionManager(source));
            var command=new ExpertProjectGrantCommand(10L,8L,"Expert","APPLICATION",1,"ReviewTask",7L,"Owner");
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<java.util.Map<String,Object>> attempt=() -> {
                if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Grant attempts did not start");
                return service.syncExpertGrant(10L,command);
            };
            try(var workers=Executors.newFixedThreadPool(2)) {
                var first=workers.submit(attempt);var second=workers.submit(attempt);start.countDown();
                var results=List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
                assertThat(results.stream().filter(result -> Boolean.TRUE.equals(result.get("changed"))).count()).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_access_grant WHERE project_id=10 AND status='ACTIVE'",Integer.class)).isEqualTo(1);
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void selectorCacheDoesNotPublishRolledBackRolesOrServeGrantValidation() throws Exception {
        try(var source=database("project")) {
            var actual=template(source,"mapper/project/UserSnapshotMapper.xml").getMapper(com.amatrix.sic.project.dao.UserSnapshotMapper.class);
            var mapper=mock(com.amatrix.sic.project.dao.UserSnapshotMapper.class,org.mockito.AdditionalAnswers.delegatesTo(actual));
            var directory=new ProjectUserDirectory(mapper,mock(IamDirectoryClient.class),mock(InternalTokenProvider.class),new ObjectMapper());
            var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
            directory.upsert(new UserSummary(7L,"selector-user","User",null,null,List.of("PROJECT_LEADER"),true,1));
            assertThat(directory.allEnabled()).hasSize(1);
            assertThat(directory.allEnabled().getFirst().roleCodes()).containsExactly("PROJECT_LEADER");
            verify(mapper,times(1)).enabled();
            assertThatThrownBy(() -> directory.allEnabled().getFirst().roleCodes().add("EXPERT"))
                    .isInstanceOf(UnsupportedOperationException.class);
            transactions.executeWithoutResult(status -> {
                directory.upsert(new UserSummary(7L,"selector-user","User",null,null,List.of("EXPERT"),true,2));
                assertThat(directory.allEnabled().getFirst().roleCodes()).containsExactly("EXPERT");
                assertThat(directory.requireAll(List.of(7L)).get(7L).roleCodes()).containsExactly("EXPERT");
                status.setRollbackOnly();
            });
            assertThat(directory.allEnabled().getFirst().roleCodes()).containsExactly("PROJECT_LEADER");
            assertThat(directory.requireAll(List.of(7L)).get(7L).roleCodes()).containsExactly("PROJECT_LEADER");
            transactions.executeWithoutResult(status -> directory.upsert(
                    new UserSummary(7L,"selector-user","User",null,null,List.of("EXPERT"),true,2)));
            assertThat(directory.allEnabled().getFirst().roleCodes()).containsExactly("EXPERT");
            assertThat(directory.requireAll(List.of(7L)).get(7L).roleCodes()).containsExactly("EXPERT");
            verify(mapper,times(3)).users(List.of(7L));
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(directory)).isFalse();
            assertThat(source.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test void competingWorkflowAttemptsCannotReleaseTheWinningMaterialLease() throws Exception {
        try(var workflow=database("workflow");var material=database("material")) {
            workflow.setMaximumPoolSize(2);material.setMaximumPoolSize(2);
            var w=new JdbcTemplate(workflow);var m=new JdbcTemplate(material);
            w.update("INSERT INTO module_instance(id,project_id,started_by_user_id,module_code,definition_id,current_node_id,current_state,current_seq,current_round,finished) VALUES(1,20,7,'CONTRACT',10,'ReviewTask','REVIEW',1,1,FALSE)");
            w.update("INSERT INTO workflow_task(module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no) VALUES(1,'ReviewTask',7,'SCIENCE_ADMIN','OPEN',1)");
            m.update("INSERT INTO material(id,project_id,material_type_code,title,created_by) VALUES(10,20,'FORM','Evidence',7)");
            m.update("INSERT INTO material_version(id,material_id,version_no,original_filename,content_type,size_bytes,sha256,object_key,file_status,uploaded_by) VALUES(30,10,1,'blank.pdf','application/pdf',3,REPEAT('a',64),'workflow-evidence','ACTIVE',7)");
            var reservationService=transactional(new com.amatrix.sic.material.service.reservation.MaterialReservationService(
                    template(material,"mapper/material/MaterialReservationMapper.xml").getMapper(com.amatrix.sic.material.dao.MaterialReservationMapper.class)),
                    new DataSourceTransactionManager(material));
            var materialClient=mock(com.amatrix.sic.workflow.client.MaterialReservationClient.class);
            var reserved=new java.util.concurrent.CountDownLatch(2);
            when(materialClient.reserve(anyString(),any())).thenAnswer(call -> {
                var result=reservationService.reserve(call.getArgument(1));
                reserved.countDown();
                if(!reserved.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Both attempts must reserve before either commits");
                return ApiResponse.ok(result);
            });
            when(materialClient.release(anyString(),anyString())).thenAnswer(call -> {
                reservationService.release(call.getArgument(1));return ApiResponse.ok(null);
            });
            var definitions=mock(com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionCatalog.class);
            var current=new com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig(
                    "ReviewTask","Review","USER_TASK","REVIEW",null,null,null,"SCIENCE_ADMIN",null,null,null,List.of(),List.of());
            var target=new com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig(
                    "ArchiveTask","Archive","USER_TASK","ARCHIVE",null,null,null,"SCIENCE_ADMIN",null,null,null,List.of(),List.of());
            var transition=new com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig(
                    "review-archive","ReviewTask","ArchiveTask","REVIEW","ARCHIVE","SUBMIT","APPROVED","NONE",null,null,null,null,List.of());
            var model=new com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult(
                    new com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.ProcessConfig("CONTRACT","Contract","CONTRACT",1,"PUBLISHED"),
                    List.of(current,target),List.of(transition));
            when(definitions.model(10L)).thenReturn(model);
            var policy=mock(com.amatrix.sic.workflow.policy.WorkflowOperationPolicy.class);
            when(policy.exactCanOperate(any(),eq(1L))).thenReturn(true);
            var tokens=mock(com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider.class);
            when(tokens.authorization()).thenReturn("Bearer lease-test");
            var outbox=mock(OutboxService.class);
            var sessions=template(workflow,"mapper/workflow/WorkflowRuntimeMapper.xml","mapper/workflow/ProjectSnapshotMapper.xml");
            var service=new com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService(
                    sessions.getMapper(com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper.class),
                    sessions.getMapper(com.amatrix.sic.workflow.dao.ProjectSnapshotMapper.class),
                    new TransactionTemplate(new DataSourceTransactionManager(workflow)),
                    mock(com.amatrix.sic.workflow.client.ProjectAccessClient.class),materialClient,tokens,policy,definitions,
                    new com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator(),
                    mock(com.amatrix.sic.workflow.service.runtime.WorkflowTransitionActionService.class),
                    mock(com.amatrix.sic.workflow.service.form.NodeFormService.class),outbox,new ObjectMapper(),
                    mock(com.amatrix.sic.workflow.policy.WorkflowProjectAccessPolicy.class),new com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy());
            var actor=new ActorContext(7L,"reviewer",null,null,List.of("SCIENCE_ADMIN"),List.of(),"lease-test",1);
            var request=new com.amatrix.sic.workflow.dto.WorkflowDtos.TransitionRequest("SUBMIT",1,"APPROVED","done",List.of(30L),null,null);
            java.util.concurrent.Callable<Object> attempt=() -> {
                try { return service.transition(1L,actor,request); }
                catch(org.springframework.web.server.ResponseStatusException ex) { return ex.getStatusCode().value(); }
            };
            try(var workers=Executors.newFixedThreadPool(2)) {
                var first=workers.submit(attempt);var second=workers.submit(attempt);
                var results=List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
                assertThat(results.stream().filter(StateTransitionContract.class::isInstance).count()).isEqualTo(1);
                assertThat(results.stream().filter(Integer.class::isInstance).toList()).containsExactly(409);
            }
            var envelope=org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
            verify(outbox).enqueue(eq("material.reservation.confirm"),envelope.capture());
            String winner=(String)((java.util.Map<?,?>)envelope.getValue().payload()).get("reservationId");
            reservationService.confirm(winner);
            assertThat(m.queryForObject("SELECT status FROM material_reservation WHERE reservation_id=?",String.class,winner)).isEqualTo("CONFIRMED");
            assertThat(m.queryForObject("SELECT COUNT(*) FROM material_reservation WHERE status='RELEASED'",Integer.class)).isEqualTo(1);
            assertThat(m.queryForObject("SELECT COUNT(DISTINCT command_id) FROM material_reservation",Integer.class)).isEqualTo(2);
            assertThat(w.queryForObject("SELECT current_seq FROM module_instance WHERE id=1",Integer.class)).isEqualTo(2);
            assertThat(w.queryForObject("SELECT COUNT(*) FROM workflow_material_reference WHERE material_version_id=30",Integer.class)).isEqualTo(1);
            verify(materialClient).release(eq("Bearer lease-test"),argThat(id -> !id.equals(winner)));
            // A rolled-back request may be submitted again with the same expectedSeq.
            // It must not inherit the first request's RELEASED reservation.
            w.update("UPDATE module_instance SET current_node_id='ReviewTask',current_state='REVIEW',current_seq=1 WHERE id=1");
            w.update("DELETE FROM state_record WHERE module_instance_id=1");
            w.update("DELETE FROM workflow_material_reference WHERE module_instance_id=1");
            reset(materialClient,outbox);
            when(materialClient.reserve(anyString(),any())).thenAnswer(call -> ApiResponse.ok(reservationService.reserve(call.getArgument(1))));
            when(materialClient.release(anyString(),anyString())).thenAnswer(call -> {
                reservationService.release(call.getArgument(1));return ApiResponse.ok(null);
            });
            doThrow(new IllegalStateException("Injected outbox failure")).doNothing().when(outbox)
                    .enqueue(eq("material.reservation.confirm"),any());
            assertThatThrownBy(() -> service.transition(1L,actor,request)).hasMessageContaining("Injected outbox failure");
            assertThat(w.queryForObject("SELECT current_seq FROM module_instance WHERE id=1",Integer.class)).isEqualTo(1);
            assertThat(w.queryForObject("SELECT COUNT(*) FROM workflow_material_reference",Integer.class)).isZero();
            service.transition(1L,actor,request);
            var retries=org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
            verify(outbox,times(2)).enqueue(eq("material.reservation.confirm"),retries.capture());
            String recovered=(String)((java.util.Map<?,?>)retries.getAllValues().getLast().payload()).get("reservationId");
            reservationService.confirm(recovered);
            assertThat(m.queryForObject("SELECT status FROM material_reservation WHERE reservation_id=?",String.class,recovered)).isEqualTo("CONFIRMED");
            assertThat(m.queryForObject("SELECT COUNT(*) FROM material_reservation WHERE status='RELEASED'",Integer.class)).isEqualTo(2);
            assertThat(m.queryForObject("SELECT COUNT(DISTINCT command_id) FROM material_reservation",Integer.class)).isEqualTo(4);
            assertThat(workflow.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(material.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactional(T target,org.springframework.transaction.PlatformTransactionManager manager) {
        var interceptor=new org.springframework.transaction.interceptor.TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        var factory=new org.springframework.aop.framework.ProxyFactory(target);
        factory.setProxyTargetClass(true);factory.addAdvice(interceptor);
        return (T)factory.getProxy();
    }

    private static HikariDataSource database(String domain) throws Exception {
        String name="sic_"+domain+"_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        var config=new HikariConfig();
        config.setJdbcUrl(mysql.getJdbcUrl().replace("/sic_mappers","/"+name));
        config.setUsername("root");config.setPassword(mysql.getPassword());
        config.setMaximumPoolSize(1);config.setConnectionTimeout(1000);
        var source=new HikariDataSource(config);
        try(var connection=source.getConnection()) {
            Path root=Path.of("../..");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("infra/mysql/schema/"+domain+".sql")));
            if(domain.equals("iam"))ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("infra/mysql/seed/iam.sql")));
        }
        return source;
    }

    static SqlSessionTemplate template(javax.sql.DataSource source,String... resources) throws Exception {
        var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
        factory.setMapperLocations(java.util.Arrays.stream(resources).map(ClassPathResource::new).toArray(ClassPathResource[]::new));
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setCallSettersOnNulls(true);
        factory.setConfiguration(configuration);factory.afterPropertiesSet();
        return new SqlSessionTemplate(factory.getObject());
    }
}
