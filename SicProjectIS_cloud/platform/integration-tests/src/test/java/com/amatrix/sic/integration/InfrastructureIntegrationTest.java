package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.amatrix.sic.dlq.DeadLetterReplayService;
import com.amatrix.sic.messaging.CloudMessagingAutoConfiguration;

@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIntegrationTest {
    private static final String MYSQL_ADMIN_USER = "root";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("sic_integration").withUsername("sic").withPassword("sic-test-password");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
            .withExposedPorts(6379);

    @Container
    static final MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-07-23T15-54-02Z"))
            .withUserName("sic-minio").withPassword("sic-minio-test-password");

    @Test
    void initializesEveryServiceWithFullSqlOnIsolatedMysqlSchemas() throws Exception {
        Map<String, String> domains = Map.of(
                "iam", "services/iam-service/src/main/resources/db/migration",
                "project", "services/project-service/src/main/resources/db/migration",
                "workflow", "services/workflow-service/src/main/resources/db/migration",
                "material", "services/material-service/src/main/resources/db/migration",
                "notification", "services/notification-service/src/main/resources/db/migration");
        for (Map.Entry<String, String> domain : domains.entrySet()) migrate(domain.getKey(), domain.getValue());
        assertThat(columnExists("sic_it_material", "generated_document", "command_id")).isTrue();
        assertThat(columnExists("sic_it_workflow", "module_instance", "started_by_user_id")).isTrue();
    }

    @Test
    void rabbitRedisAndMinioAreReachable() throws Exception {
        assertThat(rabbit.isRunning()).isTrue();
        try (Socket socket = new Socket(redis.getHost(), redis.getMappedPort(6379));
                OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            writer.write("*1\r\n$4\r\nPING\r\n");
            writer.flush();
            assertThat(reader.readLine()).isEqualTo("+PONG");
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(minio.getS3URL() + "/minio/health/live")
                .toURL().openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        assertThat(connection.getResponseCode()).isEqualTo(200);
    }

    @Test
    void replaysADeadLetterToItsOriginalRabbitRouteWithoutMessageLoss() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getAmqpPort());
        factory.setUsername(rabbit.getAdminUsername());
        factory.setPassword(rabbit.getAdminPassword());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            String sourceQueue = "integration.notification.module-events.q";
            String originalRoute = "workflow.module-state-changed";
            channel.exchangeDeclare("sic.events", "topic", true);
            channel.exchangeDeclare(CloudMessagingAutoConfiguration.DEAD_LETTER_EXCHANGE, "topic", true);
            channel.queueDeclare(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, true, false, false, null);
            channel.queueBind(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE,
                    CloudMessagingAutoConfiguration.DEAD_LETTER_EXCHANGE, "#");
            channel.queueDeclare(sourceQueue, true, false, false, Map.of(
                    "x-dead-letter-exchange", CloudMessagingAutoConfiguration.DEAD_LETTER_EXCHANGE,
                    "x-dead-letter-routing-key", "notification.module-events.dead"));
            channel.queueBind(sourceQueue, "sic.events", originalRoute);

            byte[] payload = "{\"eventId\":\"integration-event\"}".getBytes(StandardCharsets.UTF_8);
            channel.basicPublish("sic.events", originalRoute,
                    new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).build(), payload);
            var failedDelivery = channel.basicGet(sourceQueue, false);
            assertThat(failedDelivery).isNotNull();
            channel.basicNack(failedDelivery.getEnvelope().getDeliveryTag(), false, false);

            assertThat(new DeadLetterReplayService().replay(channel, 1, 5_000)).isEqualTo(1);
            var replayed = channel.basicGet(sourceQueue, true);
            assertThat(replayed).isNotNull();
            assertThat(replayed.getBody()).containsExactly(payload);
            assertThat(replayed.getProps().getHeaders()).containsEntry("x-sic-replay-count", 1);
            assertThat(channel.messageCount(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE)).isZero();
        }
    }

    @Test
    void legacyCutoverSqlPreservesEmptySeedsAndImportsNonEmptyData() throws Exception {
        Map<String, String> domains = Map.of(
                "sic_iam", "services/iam-service/src/main/resources/db/migration",
                "sic_project", "services/project-service/src/main/resources/db/migration",
                "sic_workflow", "services/workflow-service/src/main/resources/db/migration",
                "sic_material", "services/material-service/src/main/resources/db/migration",
                "sic_notification", "services/notification-service/src/main/resources/db/migration");
        for (Map.Entry<String, String> domain : domains.entrySet()) {
            migrateSchema(domain.getKey(), domain.getValue());
        }

        Path root = projectRoot();
        Path legacySchema = root.getParent().resolve(
                "SicProjectIS_backend/src/main/resources/db/full_schema_mysql.sql");
        mysql.copyFileToContainer(MountableFile.forHostPath(legacySchema), "/tmp/legacy-schema.sql");
        var legacyResult = mysql.execInContainer("sh", "-lc", "sed 's/research_project_management/sic_legacy/g' "
                + "/tmp/legacy-schema.sql | mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\"");
        assertThat(legacyResult.getExitCode())
                .as("legacy schema stdout:%n%s%nlegacy schema stderr:%n%s",
                        legacyResult.getStdout(), legacyResult.getStderr())
                .isZero();
        // The legacy schema file appends one hard-coded expert-review demo graph whose
        // module_instance_id does not exist. Remove that fixture so this test really is
        // the advertised empty baseline; production verification must still reject it.
        var removeDemoResult = mysql.execInContainer("sh", "-lc",
                "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" sic_legacy -e \""
                        + "DELETE FROM expert_review_score; "
                        + "DELETE FROM expert_review_assignment; "
                        + "DELETE FROM expert_review_batch\"");
        assertThat(removeDemoResult.getExitCode())
                .as("demo cleanup stdout:%n%s%ndemo cleanup stderr:%n%s",
                        removeDemoResult.getStdout(), removeDemoResult.getStderr())
                .isZero();
        var manifestResult = mysql.execInContainer("sh", "-lc", "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" "
                + "sic_legacy -e \"CREATE TABLE migration_file_manifest (material_version_id BIGINT PRIMARY KEY, "
                + "source_file_url VARCHAR(1000) NOT NULL, source_absolute_path VARCHAR(2000) NOT NULL, "
                + "object_key VARCHAR(500) NOT NULL UNIQUE, sha256 CHAR(64) NOT NULL, size_bytes BIGINT NOT NULL, "
                + "content_type VARCHAR(128) NOT NULL, migrated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))\"");
        assertThat(manifestResult.getExitCode())
                .as("manifest stdout:%n%s%nmanifest stderr:%n%s",
                        manifestResult.getStdout(), manifestResult.getStderr())
                .isZero();

        mysql.copyFileToContainer(MountableFile.forHostPath(root.resolve("infra/migration/import-legacy.sql")),
                "/tmp/import-legacy.sql");
        mysql.copyFileToContainer(MountableFile.forHostPath(root.resolve("infra/migration/verify-legacy-import.sql")),
                "/tmp/verify-legacy-import.sql");
        var importResult = mysql.execInContainer("sh", "-lc",
                "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" < /tmp/import-legacy.sql");
        assertThat(importResult.getExitCode())
                .as("import stdout:%n%s%nimport stderr:%n%s",
                        importResult.getStdout(), importResult.getStderr())
                .isZero();
        var verifyResult = mysql.execInContainer("sh", "-lc",
                "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" < /tmp/verify-legacy-import.sql");
        assertThat(verifyResult.getExitCode())
                .as("verification stdout:%n%s%nverification stderr:%n%s",
                        verifyResult.getStdout(), verifyResult.getStderr())
                .isZero();
        assertThat(verifyResult.getStdout()).contains("database verification complete");
        // Copy the release's real BPMN and immutable node IDs into a legacy fixture;
        // import must preserve those IDs, runtime sequence and material references.
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),MYSQL_ADMIN_USER,mysql.getPassword());
                var statement=connection.createStatement()) {
            String[] fixture={
                "INSERT INTO sic_legacy.department(dept_id,dept_code,dept_name,enabled) VALUES(1,'LEGACY_DEPT','Legacy department',TRUE)",
                "INSERT INTO sic_legacy.app_user(user_id,username,password_hash,real_name,dept_id,enabled) VALUES(7,'legacy-user','fixture-password-hash','Legacy User',1,TRUE)",
                "INSERT INTO sic_legacy.role(role_id,role_code,role_name,enabled) VALUES(200,'PROJECT_LEADER','Leader',TRUE),(201,'EXPERT','Expert',TRUE)",
                "INSERT INTO sic_legacy.user_role(user_id,role_id) VALUES(7,200),(7,201)",
                "INSERT INTO sic_legacy.expert_qualification_application(application_id,applicant_user_id,applicant_dept_id,specialty,application_reason,status,created_at) VALUES(50,7,1,'Research','Migration evidence','APPROVED',NOW())",
                "INSERT INTO sic_legacy.project(project_id,project_code,project_name,leader_user_id,dept_id,lifecycle_stage) VALUES(20,'LEGACY-20','Legacy project',7,1,'APPLICATION')",
                "INSERT INTO sic_legacy.project_member(project_member_id,project_id,user_id,member_role) VALUES(30,20,7,'MEMBER')",
                "INSERT INTO sic_legacy.workflow_definition(workflow_definition_id,process_key,process_name,module_type,bpmn_xml,state_machine_rules_json,version_no,status) SELECT id,code,name,code,bpmn_xml,definition_json,version,status FROM sic_workflow.workflow_definition",
                "INSERT INTO sic_legacy.workflow_node(workflow_node_id,workflow_definition_id,node_id,node_name,node_type,state_code,lane_name,responsible_actor_code,responsible_actor_name,candidate_role_code,operation_mode,represented_actor_code,represented_actor_name) SELECT id,workflow_definition_id,node_id,node_name,node_type,state_code,lane_name,responsible_actor_code,responsible_actor_name,candidate_role_code,operation_mode,represented_actor_code,represented_actor_name FROM sic_workflow.workflow_node_config",
                "INSERT INTO sic_legacy.project_module_instance(module_instance_id,project_id,module_type,workflow_definition_id) SELECT 100,20,'APPLICATION',id FROM sic_workflow.workflow_definition WHERE code='APPLICATION'",
                "INSERT INTO sic_legacy.module_state_record(state_record_id,module_instance_id,seq,round_no,event_type,to_state,to_node_id) VALUES(110,100,1,1,'APPLICATION_PROCESS_STARTED','APPLICATION_DRAFT','SubmitApplicationTask')",
                "INSERT INTO sic_legacy.task_instance(task_instance_id,module_instance_id,node_id,state_code,assignee_user_id,candidate_role_code,task_status,round_no) VALUES(120,100,'SubmitApplicationTask','APPLICATION_DRAFT',7,'PROJECT_LEADER','OPEN',1)",
                "INSERT INTO sic_legacy.project_application(application_id,project_id,application_title,is_limited_project,application_summary) VALUES(130,20,'Legacy application',TRUE,'Preserve draft')",
                "INSERT INTO sic_legacy.material_type(material_type_id,material_type_code,material_type_name,module_type,allowed_file_types,max_file_size_mb,enabled) VALUES(140,'FORM','Legacy form','APPLICATION','pdf',10,TRUE)",
                "INSERT INTO sic_legacy.material(material_id,project_id,material_type_id,created_by) VALUES(150,20,140,7)",
                "INSERT INTO sic_legacy.material_version(material_version_id,material_id,version_no,file_name,file_url,uploaded_by,is_current) VALUES(160,150,1,'blank.pdf','uploads/blank.pdf',7,TRUE)",
                "INSERT INTO sic_legacy.migration_file_manifest(material_version_id,source_file_url,source_absolute_path,object_key,sha256,size_bytes,content_type) VALUES(160,'uploads/blank.pdf','/fixture/blank.pdf','legacy/material-versions/160/blank.pdf',REPEAT('a',64),535,'application/pdf')",
                "INSERT INTO sic_legacy.state_record_material(state_record_id,material_version_id,material_usage,is_required) VALUES(110,160,'SUBMITTED',TRUE)"
            };
            for(String sql:fixture)statement.executeUpdate(sql);
        }
        var nonEmptyImport=mysql.execInContainer("sh","-lc","MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot < /tmp/import-legacy.sql");
        assertThat(nonEmptyImport.getExitCode()).as("non-empty import: %s",nonEmptyImport.getStderr()).isZero();
        var nonEmptyVerify=mysql.execInContainer("sh","-lc","MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot < /tmp/verify-legacy-import.sql");
        assertThat(nonEmptyVerify.getExitCode()).as("non-empty verification: %s %s",nonEmptyVerify.getStdout(),nonEmptyVerify.getStderr()).isZero();
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),MYSQL_ADMIN_USER,mysql.getPassword());
                var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT current_seq,current_round,current_node_id FROM sic_workflow.module_instance WHERE id=100")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);assertThat(rows.getString(3)).isEqualTo("SubmitApplicationTask");
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM sic_workflow.workflow_material_reference WHERE module_instance_id=100 AND seq=1 AND material_version_id=160")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM sic_iam.expert_qualification WHERE id=50 AND user_id=7 AND status='APPROVED'")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
        var workflowSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl().replace("/sic_integration","/sic_workflow"),MYSQL_ADMIN_USER,mysql.getPassword());
        var sessions=MysqlMapperIntegrationTest.template(workflowSource,"mapper/workflow/WorkflowCatalogMapper.xml",
                "mapper/workflow/WorkflowRuntimeMapper.xml","mapper/workflow/ProjectSnapshotMapper.xml");
        var operation=org.mockito.Mockito.mock(com.amatrix.sic.workflow.policy.WorkflowOperationPolicy.class);
        org.mockito.Mockito.when(operation.runtimeAccess(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(100L),org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new com.amatrix.sic.workflow.policy.WorkflowOperationPolicy.RuntimeAccess(true,true));
        var access=org.mockito.Mockito.mock(com.amatrix.sic.workflow.policy.WorkflowProjectAccessPolicy.class);
        org.mockito.Mockito.when(access.canAccessModule(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(100L))).thenReturn(true);
        var forms=org.mockito.Mockito.mock(com.amatrix.sic.workflow.service.form.NodeFormService.class);
        org.mockito.Mockito.when(forms.forNode(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of());
        var catalog=new com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionCatalog(
                sessions.getMapper(com.amatrix.sic.workflow.dao.WorkflowCatalogMapper.class),new com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser());
        var runtime=new com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService(
                sessions.getMapper(com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper.class),sessions.getMapper(com.amatrix.sic.workflow.dao.ProjectSnapshotMapper.class),
                new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(workflowSource)),
                org.mockito.Mockito.mock(com.amatrix.sic.workflow.client.ProjectAccessClient.class),org.mockito.Mockito.mock(com.amatrix.sic.workflow.client.MaterialReservationClient.class),
                org.mockito.Mockito.mock(com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider.class),operation,catalog,
                new com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator(),org.mockito.Mockito.mock(com.amatrix.sic.workflow.service.runtime.WorkflowTransitionActionService.class),
                forms,org.mockito.Mockito.mock(com.amatrix.sic.messaging.OutboxService.class),new tools.jackson.databind.ObjectMapper(),access,new com.amatrix.sic.workflow.policy.ModuleSuccessorPolicy());
        var view=runtime.runtimeView(100L,new com.amatrix.sic.contracts.ActorContext(7L,"legacy-user",1L,"Legacy department",java.util.List.of("PROJECT_LEADER"),java.util.List.of(),"legacy-read",1));
        assertThat((java.util.List<?>)view.get("availableTransitions")).isNotEmpty();
        assertThat(((java.util.Map<?,?>)view.get("context")).get("currentNodeId")).isEqualTo("SubmitApplicationTask");
        var forbiddenRerun=mysql.execInContainer("sh","-lc","MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot < /tmp/import-legacy.sql");
        assertThat(forbiddenRerun.getExitCode()).isNotZero();
        assertThat(forbiddenRerun.getStderr()).contains("Destination business tables are not empty");
    }

    private static void migrate(String domain, String domainLocation) throws Exception {
        migrateSchema("sic_it_" + domain, domainLocation);
    }

    private static void migrateSchema(String schema, String domainLocation) throws Exception {
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), MYSQL_ADMIN_USER, mysql.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + schema
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String domain=schema.substring(schema.lastIndexOf('_')+1);
        try(var connection=DriverManager.getConnection(url,MYSQL_ADMIN_USER,mysql.getPassword())) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource(projectRoot().resolve("infra/mysql/schema/"+domain+".sql")));
            if(domain.equals("iam")) org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource(projectRoot().resolve("infra/mysql/seed/iam.sql")));
            if(domain.equals("workflow")) org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource(projectRoot().resolve("infra/mysql/seed/workflow-assets.sql")));
        }
    }

    private static boolean columnExists(String schema, String table, String column) throws Exception {
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), MYSQL_ADMIN_USER, mysql.getPassword());
                var statement = connection.prepareStatement("""
                        SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema=? AND table_name=? AND column_name=?
                        """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) == 1;
            }
        }
    }

    private static String location(String relative) {
        Path root = projectRoot();
        return "filesystem:" + root.resolve(relative).toAbsolutePath().toString().replace('\\', '/');
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.isRegularFile(current.resolve("pom.xml"))
                    && java.nio.file.Files.isDirectory(current.resolve("services"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sic cloud project root");
    }
}
