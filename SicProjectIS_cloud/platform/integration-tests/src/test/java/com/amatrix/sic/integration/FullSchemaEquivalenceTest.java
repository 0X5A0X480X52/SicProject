package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Release gate: compare actual MySQL metadata, not a textual SQL concatenation. */
@Testcontainers(disabledWithoutDocker=true)
class FullSchemaEquivalenceTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sic_comparison").withUsername("sic").withPassword("comparison-only-password")
            .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_0900_ai_ci");
    private static final List<String> DOMAINS=List.of("iam","project","workflow","material","notification");

    @Test void newExpertGrantIsEffectiveWithinTheSameFractionalSecond() throws Exception {
        try(Connection connection=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword())) {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../infra/mysql/schema/project.sql")));
            // Freeze this MySQL session clock to make the subsecond regression deterministic.
            jdbc.execute("SET timestamp=1791619600.5");
            jdbc.update("INSERT INTO project(project_code,project_name,owner_user_id,owner_name_snapshot,lifecycle_status) VALUES('CLOCK','Clock',1,'Owner','ACTIVE')");
            jdbc.update("INSERT INTO project_access_grant(project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,module_type,round_no,task_node_id,granted_by,granted_by_name_snapshot) VALUES(1,2,'Expert','PROJECT_MODULE_EXPERT_ASSIGNMENT','APPLICATION',1,'ScienceExpertReviewTask',1,'Owner')");
            assertThat(jdbc.queryForObject("SELECT effective_from>CURRENT_TIMESTAMP FROM project_access_grant",Boolean.class)).isTrue();
            var actor=new com.amatrix.sic.contracts.ActorContext(2L,"expert",null,null,List.of("EXPERT"),List.of(),"clock-test",1);
            var target=new com.amatrix.sic.contracts.ProjectOperationTarget("test",1L,"OPERATE_EXPERT_TASK","APPLICATION",1,"ScienceExpertReviewTask");
            var result=new com.amatrix.sic.project.policy.ProjectAccessPolicy(MysqlMapperIntegrationTest.template(jdbc.getDataSource(),"mapper/project/ProjectScopeMapper.xml").getMapper(com.amatrix.sic.project.dao.ProjectScopeMapper.class)).decideBatch(actor,List.of(target));
            assertThat(result.getFirst().allowed()).isTrue();
            assertThat(new com.amatrix.sic.project.service.ProjectQueryService(MysqlMapperIntegrationTest.template(jdbc.getDataSource(),"mapper/project/ProjectQueryMapper.xml").getMapper(com.amatrix.sic.project.dao.ProjectQueryMapper.class)).accessibleProjects(2L,null,List.of("EXPERT"))).hasSize(1);
        }
    }

    @Test void fullSqlMatchesEveryMigratedColumnAndIndexWithoutHistoryTables() throws Exception {
        Path root=Path.of("../..").toAbsolutePath().normalize();
        int total=0;
        var signature=new java.util.LinkedHashMap<String,Object>();
        try(Connection admin=DriverManager.getConnection(mysql.getJdbcUrl(),"root",mysql.getPassword())) {
            for(String domain:DOMAINS) {
                String oldSchema="sic_old_"+domain,newSchema="sic_new_"+domain;
                for(String schema:List.of(oldSchema,newSchema))
                    admin.createStatement().execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                String oldUrl=mysql.getJdbcUrl().replace("/sic_comparison","/"+oldSchema);
                Flyway.configure().dataSource(oldUrl,"root",mysql.getPassword()).locations(
                    "filesystem:"+root.resolve("platform/cloud-messaging/src/main/resources/db/common"),
                    "filesystem:"+root.resolve("services/"+domain+"-service/src/main/resources/db/migration"))
                    .load().migrate();
                String newUrl=mysql.getJdbcUrl().replace("/sic_comparison","/"+newSchema);
                try(Connection fresh=DriverManager.getConnection(newUrl,"root",mysql.getPassword())) {
                    ScriptUtils.executeSqlScript(fresh,new FileSystemResource(root.resolve("infra/mysql/schema/"+domain+".sql")));
                    if(domain.equals("iam")) ScriptUtils.executeSqlScript(fresh,new FileSystemResource(root.resolve("infra/mysql/seed/iam.sql")));
                    if(domain.equals("workflow")) {
                        Path seed=root.resolve("infra/mysql/seed/workflow-assets.sql");
                        assertThat(Files.exists(seed)).as("Offline Flowable seed must be generated").isTrue();
                        ScriptUtils.executeSqlScript(fresh,new FileSystemResource(seed));
                        assertThat(query(fresh,"SELECT code,status,published FROM workflow_definition ORDER BY code")).hasSize(3);
                        assertThat(query(fresh,"SELECT id FROM workflow_node_config").size()).isGreaterThan(37);
                    }
                }
                List<String> tables=query(admin,"SELECT table_name FROM information_schema.tables WHERE table_schema='"+newSchema+"' ORDER BY table_name");
                total+=tables.size();
                var domainSignature=new java.util.LinkedHashMap<String,Object>();
                signature.put(domain,domainSignature);
                assertThat(tables).doesNotContain("flyway_schema_history");
                assertThat(tables).isEqualTo(query(admin,"SELECT table_name FROM information_schema.tables WHERE table_schema='"+oldSchema+"' AND table_name<>'flyway_schema_history' ORDER BY table_name"));
                for(String table:tables) {
                    String columns="SELECT column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(collation_name,'<NULL>') FROM information_schema.columns WHERE table_schema='%s' AND table_name='"+table+"' ORDER BY ordinal_position";
                    String indices="SELECT index_name,non_unique,seq_in_index,column_name,COALESCE(sub_part,-1),index_type FROM information_schema.statistics WHERE table_schema='%s' AND table_name='"+table+"' ORDER BY index_name,seq_in_index";
                    assertThat(query(admin,columns.formatted(newSchema))).as(domain+"."+table+" columns").isEqualTo(query(admin,columns.formatted(oldSchema)));
                    assertThat(query(admin,indices.formatted(newSchema))).as(domain+"."+table+" indices").isEqualTo(query(admin,indices.formatted(oldSchema)));
                    String constraints="SELECT constraint_name,constraint_type FROM information_schema.table_constraints WHERE table_schema='%s' AND table_name='"+table+"' ORDER BY constraint_name";
                    String metadata="SELECT engine,table_collation,row_format FROM information_schema.tables WHERE table_schema='%s' AND table_name='"+table+"'";
                    assertThat(query(admin,constraints.formatted(newSchema))).as(domain+"."+table+" constraints").isEqualTo(query(admin,constraints.formatted(oldSchema)));
                    assertThat(query(admin,metadata.formatted(newSchema))).as(domain+"."+table+" metadata").isEqualTo(query(admin,metadata.formatted(oldSchema)));
                    domainSignature.put(table,java.util.Map.of("columns",query(admin,columns.formatted(newSchema)),
                            "indices",query(admin,indices.formatted(newSchema)),"constraints",query(admin,constraints.formatted(newSchema)),
                            "metadata",query(admin,metadata.formatted(newSchema))));
                }
            }
        }
        assertThat(total).isEqualTo(52);
        var mapper=new tools.jackson.databind.ObjectMapper();
        Path expected=root.resolve("infra/mysql/schema-signature.json");
        if(Boolean.getBoolean("sic.schema.update-signature"))
            Files.writeString(expected,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(signature)+"\n");
        assertThat(Files.exists(expected)).as("Generate checked MySQL metadata before SQL cutover").isTrue();
        assertThat(mapper.readValue(Files.readString(expected),java.util.Map.class)).isEqualTo(signature);
    }

    private static List<String> query(Connection connection,String sql) throws Exception {
        List<String> rows=new ArrayList<>();
        try(var statement=connection.createStatement();var result=statement.executeQuery(sql)) {
            while(result.next()) {
                List<String> fields=new ArrayList<>();
                for(int i=1;i<=result.getMetaData().getColumnCount();i++)fields.add(result.getString(i));
                rows.add(String.join("|",fields));
            }
        }
        return rows;
    }
}
