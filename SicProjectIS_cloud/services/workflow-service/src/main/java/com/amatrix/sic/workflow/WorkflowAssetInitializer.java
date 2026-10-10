package com.amatrix.sic.workflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowAssetInitializer implements ApplicationRunner {
    static final String BASE_PATH = "workflows/bpmn_auxiliary_tags_augmented/";
    static final List<String> ASSET_NAMES = List.of(
            "项目申请_辅助标签版.bpmn",
            "纵向项目合同_辅助标签版.bpmn",
            "项目结题_辅助标签版.bpmn");

    private final JdbcTemplate jdbc;
    private final FlowableBpmnDefinitionParser parser;
    private final ObjectMapper objectMapper;

    public WorkflowAssetInitializer(JdbcTemplate jdbc, FlowableBpmnDefinitionParser parser, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String assetName : ASSET_NAMES) {
            install(assetName, read(assetName));
        }
    }

    private void install(String assetName, String bpmnXml) {
        WorkflowBpmnParseResult model = parser.parse(bpmnXml);
        if (model.nodes().isEmpty() || model.transitions().isEmpty()) {
            throw new IllegalStateException("Workflow asset has no executable model: " + assetName);
        }
        String moduleType = required(model.processConfig().moduleType(), "moduleType", assetName);
        int version = model.processConfig().versionNo() == null ? 1 : model.processConfig().versionNo();
        boolean newerPublished = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM workflow_definition WHERE code=? AND version>? AND published=TRUE)
                """, Boolean.class, moduleType, version));
        List<Long> existing = jdbc.query("SELECT id FROM workflow_definition WHERE code=? AND version=?",
                (rs, row) -> rs.getLong(1), moduleType, version);
        Long definitionId;
        if (existing.isEmpty()) {
            GeneratedKeyHolder holder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO workflow_definition(code,name,version,status,published,bpmn_xml,definition_json)
                        VALUES (?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, moduleType);
                statement.setString(2, Objects.requireNonNullElse(model.processConfig().processName(), moduleType));
                statement.setInt(3, version);
                statement.setString(4, newerPublished ? "INACTIVE" : "ACTIVE");
                statement.setBoolean(5, !newerPublished);
                statement.setString(6, bpmnXml);
                statement.setString(7, write(model));
                return statement;
            }, holder);
            definitionId = Objects.requireNonNull(holder.getKey()).longValue();
        } else {
            definitionId = existing.getFirst();
            jdbc.update("""
                    UPDATE workflow_definition SET name=?,status=?,published=?,bpmn_xml=?,definition_json=? WHERE id=?
                    """, Objects.requireNonNullElse(model.processConfig().processName(), moduleType),
                    newerPublished ? "INACTIVE" : "ACTIVE", !newerPublished, bpmnXml, write(model), definitionId);
        }
        persistModel(definitionId, model);
    }

    void persistModel(Long definitionId, WorkflowBpmnParseResult model) {
        jdbc.update("DELETE FROM workflow_transition_config WHERE workflow_definition_id=?", definitionId);
        jdbc.update("DELETE FROM workflow_node_config WHERE workflow_definition_id=?", definitionId);
        for (WorkflowBpmnParseResult.NodeConfig node : model.nodes()) {
            jdbc.update("""
                    INSERT INTO workflow_node_config(workflow_definition_id,node_id,node_name,node_type,state_code,
                      lane_name,responsible_actor_code,responsible_actor_name,candidate_role_code,operation_mode,
                      represented_actor_code,represented_actor_name,material_requirements_json,document_configs_json)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, definitionId, node.nodeId(), node.nodeName(), node.nodeType(), node.stateCode(), node.laneName(),
                    node.responsibleActorCode(), node.responsibleActorName(), node.candidateRoleCode(), node.operationMode(),
                    node.representedActorCode(), node.representedActorName(), write(node.materialRequirements()),
                    write(node.documentConfigs()));
        }
        for (WorkflowBpmnParseResult.TransitionConfig transition : model.transitions()) {
            jdbc.update("""
                    INSERT INTO workflow_transition_config(workflow_definition_id,transition_id,source_ref,target_ref,
                      source_state_code,target_state_code,event_type,result_code,condition_type,condition_key,
                      condition_value,condition_handler_key,condition_expression,action_keys_json)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, definitionId, transition.transitionId(), transition.sourceRef(), transition.targetRef(),
                    transition.sourceStateCode(), transition.targetStateCode(), transition.eventType(), transition.result(),
                    transition.conditionType(), transition.conditionKey(), transition.conditionValue(),
                    transition.conditionHandlerKey(), transition.conditionExpression(), write(transition.actionKeys()));
        }
    }

    static String read(String assetName) {
        try {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + assetName);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read workflow asset: " + assetName, ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize workflow model", ex);
        }
    }

    private static String required(String value, String field, String assetName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Workflow asset " + assetName + " is missing " + field);
        }
        return value;
    }
}
