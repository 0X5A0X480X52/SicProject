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

import org.flywaydb.core.Flyway;
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
    void appliesEveryServiceMigrationToIsolatedMysqlSchemas() throws Exception {
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
    void legacyCutoverSqlExecutesAndPassesEmptyBaselineVerification() throws Exception {
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
        Flyway flyway = Flyway.configure().dataSource(url, MYSQL_ADMIN_USER, mysql.getPassword())
                .locations(location("platform/cloud-messaging/src/main/resources/db/common"), location(domainLocation))
                .load();
        assertThat(flyway.migrate().success).isTrue();
        assertThat(flyway.info().applied()).isNotEmpty();
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
