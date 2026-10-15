package com.amatrix.sic.workflow;

import com.amatrix.sic.workflow.service.definition.WorkflowDefinitionModelStore;
import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.bootstrap.WorkflowAssetInitializer;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WorkflowAssetRestartTest {
    @Test void restartPreservesDefinitionIdsPublishedChangesAndConfiguredNodes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:restart-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        String schema=Files.readString(Path.of("../../infra/mysql/schema/workflow.sql"))
                .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
        var jdbc=new JdbcTemplate(source);
        // Existing migration placeholders exercise the bootstrap path and its module reference check.
        for(String type:java.util.List.of("APPLICATION","CONTRACT","ACCEPTANCE"))
            jdbc.update("INSERT INTO workflow_definition(code,name,version,status,published,bpmn_xml,definition_json) VALUES(?,?,1,'ACTIVE',TRUE,'','{}')",type,type);
        var definitions=WorkflowTestMapper.create(source,com.amatrix.sic.workflow.dao.WorkflowDefinitionMapper.class,"mapper/workflow/WorkflowDefinitionMapper.xml");
        var json=new ObjectMapper();
        var assets=new WorkflowAssetInitializer(definitions,new FlowableBpmnDefinitionParser(),json,new WorkflowDefinitionModelStore(definitions,json));
        ReflectionTestUtils.setField(assets,"bootstrapMissing",true);
        var args=new DefaultApplicationArguments(new String[0]);
        assets.run(args);
        var ids=jdbc.queryForList("SELECT id FROM workflow_node_config ORDER BY id",Long.class);
        assertThat(ids).hasSizeGreaterThan(37);
        jdbc.update("UPDATE workflow_node_config SET node_name='Published customization' WHERE id=?",ids.getFirst());
        jdbc.update("UPDATE workflow_definition SET published=FALSE WHERE code='APPLICATION'");
        ReflectionTestUtils.setField(assets,"bootstrapMissing",false);
        assets.run(args);
        assertThat(jdbc.queryForList("SELECT id FROM workflow_node_config ORDER BY id",Long.class)).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT node_name FROM workflow_node_config WHERE id=?",String.class,ids.getFirst())).isEqualTo("Published customization");
        assertThat(jdbc.queryForObject("SELECT published FROM workflow_definition WHERE code='APPLICATION'",Boolean.class)).isFalse();
    }
}
