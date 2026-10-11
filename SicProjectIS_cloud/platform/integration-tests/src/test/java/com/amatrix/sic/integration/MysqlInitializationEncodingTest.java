package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Executes the actual Docker initialization path, not JDBC instead of the shell importer. */
@Testcontainers(disabledWithoutDocker=true)
class MysqlInitializationEncodingTest {
    private static final Path ROOT=Path.of("../..").toAbsolutePath().normalize();
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("test").withUsername("test").withPassword("initialization-test-password")
            .withEnv("IAM_DB_PASSWORD","iam-test-password").withEnv("PROJECT_DB_PASSWORD","project-test-password")
            .withEnv("WORKFLOW_DB_PASSWORD","workflow-test-password").withEnv("MATERIAL_DB_PASSWORD","material-test-password")
            .withEnv("NOTIFICATION_DB_PASSWORD","notification-test-password")
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("infra/mysql/init-databases.sh"),0755),"/docker-entrypoint-initdb.d/01-init-databases.sh")
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("infra/mysql/schema")),"/sic-release/schema")
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("infra/mysql/seed")),"/sic-release/seed")
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("infra/mysql/patches/20261011_01_repair_iam_seed_text.sql")),"/sic-release/repair.sql")
            .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_0900_ai_ci");

    private org.springframework.jdbc.core.JdbcTemplate jdbc() throws Exception {
        var connection=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword());
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
    }

    @Test void actualShellInitializationPreservesEveryChineseSeedLabel() throws Exception {
        var jdbc=jdbc();
        try {
            String historical=Files.readString(ROOT.resolve("services/iam-service/src/main/resources/db/migration/V2__iam_domain.sql"));
            var pattern=Pattern.compile("\\('([^']+)', '([^']+)'(?:, '([^']+)', TRUE)?\\)");
            var matches=pattern.matcher(historical);
            int fields=0;
            while(matches.find()) {
                String table=matches.group(3)==null?"app_permission":"app_role";
                assertThat(jdbc.queryForObject("SELECT name FROM sic_iam."+table+" WHERE code=?",String.class,matches.group(1)))
                        .isEqualTo(matches.group(2));
                fields++;
                if(matches.group(3)!=null) {
                    assertThat(jdbc.queryForObject("SELECT description FROM sic_iam.app_role WHERE code=?",String.class,matches.group(1)))
                            .isEqualTo(matches.group(3));fields++;
                }
            }
            assertThat(fields).isEqualTo(35);
            assertThat(jdbc.queryForObject("SELECT name FROM sic_iam.department WHERE id=1",String.class)).isEqualTo("系统管理部门");
        } finally { ((org.springframework.jdbc.datasource.SingleConnectionDataSource)jdbc.getDataSource()).destroy(); }
    }

    @Test void repairRestoresOnlyExactCorruptionAndIsIdempotentWithoutChangingAccess() throws Exception {
        var jdbc=jdbc();
        try {
            jdbc.execute("CREATE DATABASE sic_patch_test CHARACTER SET utf8mb4");
            for(String table:List.of("department","app_role","app_permission","role_permission","user_role","app_user")) {
                jdbc.execute("CREATE TABLE sic_patch_test."+table+" LIKE sic_iam."+table);
                jdbc.execute("INSERT INTO sic_patch_test."+table+" SELECT * FROM sic_iam."+table);
            }
            jdbc.update("INSERT INTO sic_patch_test.app_user(id,username,password_hash,real_name,dept_id,auth_version) VALUES(100,'repair-test','unused','用户',1,7)");
            jdbc.update("INSERT INTO sic_patch_test.user_role SELECT 100,id FROM sic_patch_test.app_role WHERE code='PROJECT_LEADER'");
            var users=jdbc.queryForList("SELECT * FROM sic_patch_test.app_user");
            var userRoles=jdbc.queryForList("SELECT * FROM sic_patch_test.user_role");
            var permissions=jdbc.queryForList("SELECT * FROM sic_patch_test.role_permission ORDER BY role_id,permission_id");
            for(String table:List.of("department","app_role","app_permission"))
                jdbc.update("UPDATE sic_patch_test."+table+" SET name=CONVERT(CONVERT(CAST(name AS BINARY) USING latin1) USING utf8mb4)");
            jdbc.update("UPDATE sic_patch_test.app_role SET description=CONVERT(CONVERT(CAST(description AS BINARY) USING latin1) USING utf8mb4)");
            jdbc.update("UPDATE sic_patch_test.app_role SET name=? WHERE code='EXPERT'","自定义专家名称");
            var result=mysql.execInContainer("sh","-c","MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql --default-character-set=utf8mb4 -uroot sic_patch_test < /sic-release/repair.sql");
            assertThat(result.getExitCode()).as(result.getStderr()).isZero();
            assertThat(jdbc.queryForObject("SELECT name FROM sic_patch_test.department WHERE id=1",String.class)).isEqualTo("系统管理部门");
            assertThat(jdbc.queryForObject("SELECT name FROM sic_patch_test.app_role WHERE code='EXPERT'",String.class)).isEqualTo("自定义专家名称");
            for(String table:List.of("app_role","app_permission")) {
                String condition=table.equals("app_role")?" AND a.code<>'EXPERT'":"";
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sic_patch_test."+table+" a JOIN sic_iam."+table+" b ON a.id=b.id WHERE BINARY a.name<>BINARY b.name"+condition,Integer.class)).isZero();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sic_patch_test.app_role a JOIN sic_iam.app_role b ON a.id=b.id WHERE BINARY a.description<>BINARY b.description",Integer.class)).isZero();
            var checkpoint=jdbc.queryForList("SELECT * FROM sic_patch_test.app_role ORDER BY id");
            result=mysql.execInContainer("sh","-c","MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql --default-character-set=utf8mb4 -uroot sic_patch_test < /sic-release/repair.sql");
            assertThat(result.getExitCode()).isZero();
            assertThat(jdbc.queryForList("SELECT * FROM sic_patch_test.app_role ORDER BY id")).isEqualTo(checkpoint);
            assertThat(jdbc.queryForList("SELECT * FROM sic_patch_test.app_user")).isEqualTo(users);
            assertThat(jdbc.queryForList("SELECT * FROM sic_patch_test.user_role")).isEqualTo(userRoles);
            assertThat(jdbc.queryForList("SELECT * FROM sic_patch_test.role_permission ORDER BY role_id,permission_id")).isEqualTo(permissions);
        } finally { ((org.springframework.jdbc.datasource.SingleConnectionDataSource)jdbc.getDataSource()).destroy(); }
    }
}
