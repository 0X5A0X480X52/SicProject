package com.amatrix.sic.workflow.tools.seed;

import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowAssetCatalog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/** Offline release tool, using the same Flowable parser as the runtime. */
public final class WorkflowSeedExporter {
    private WorkflowSeedExporter() {}
    public static void main(String[] args) throws Exception {
        if(args.length<1 || args.length>2 || (args.length==2 && !args[1].equals("--missing-only")))
            throw new IllegalArgumentException("Expected output SQL path [--missing-only]");
        boolean missingOnly=args.length==2;
        ObjectMapper mapper = new ObjectMapper();
        FlowableBpmnDefinitionParser parser = new FlowableBpmnDefinitionParser();
        StringBuilder sql = new StringBuilder(missingOnly
                ? "-- Explicit adoption seed: add missing module codes only; never overwrite installed assets.\n"
                : "-- Real BPMN release seed; empty workflow database only.\n");
        sql.append("START TRANSACTION;\n");
        for(String name : WorkflowAssetCatalog.names()) {
            String xml = WorkflowAssetCatalog.read(name);
            WorkflowBpmnParseResult model = parser.parse(xml);
            var process = model.processConfig();
            sql.append("INSERT INTO workflow_definition(code,name,version,status,published,bpmn_xml,definition_json) ")
                    .append(missingOnly?"SELECT ":"VALUES (")
                    .append(values(process.moduleType(),process.processName(),process.versionNo()==null?1:process.versionNo(),
                            "ACTIVE",true,xml,mapper.writeValueAsString(model)));
            if(missingOnly)sql.append(" WHERE NOT EXISTS(SELECT 1 FROM workflow_definition WHERE code=")
                    .append(values(process.moduleType())).append(");\nSET @definition_id=IF(ROW_COUNT()=1,LAST_INSERT_ID(),NULL);\n");
            else sql.append(");\nSET @definition_id=LAST_INSERT_ID();\n");
            for(var n : model.nodes()) {
                sql.append("INSERT INTO workflow_node_config(workflow_definition_id,node_id,node_name,node_type,state_code,lane_name,responsible_actor_code,responsible_actor_name,candidate_role_code,operation_mode,represented_actor_code,represented_actor_name,material_requirements_json,document_configs_json) ")
                        .append(missingOnly?"SELECT @definition_id,":"VALUES (@definition_id,")
                        .append(values(n.nodeId(),n.nodeName(),n.nodeType(),n.stateCode(),n.laneName(),n.responsibleActorCode(),
                            n.responsibleActorName(),n.candidateRoleCode(),n.operationMode(),n.representedActorCode(),n.representedActorName(),
                            mapper.writeValueAsString(n.materialRequirements()),mapper.writeValueAsString(n.documentConfigs())))
                        .append(missingOnly?" WHERE @definition_id IS NOT NULL;\n":");\n");
            }
            for(var t : model.transitions()) {
                sql.append("INSERT INTO workflow_transition_config(workflow_definition_id,transition_id,source_ref,target_ref,source_state_code,target_state_code,event_type,result_code,condition_type,condition_key,condition_value,condition_handler_key,condition_expression,action_keys_json) ")
                        .append(missingOnly?"SELECT @definition_id,":"VALUES (@definition_id,")
                        .append(values(t.transitionId(),t.sourceRef(),t.targetRef(),t.sourceStateCode(),t.targetStateCode(),t.eventType(),
                            t.result(),t.conditionType(),t.conditionKey(),t.conditionValue(),t.conditionHandlerKey(),t.conditionExpression(),
                            mapper.writeValueAsString(t.actionKeys())))
                        .append(missingOnly?" WHERE @definition_id IS NOT NULL;\n":");\n");
            }
        }
        sql.append("COMMIT;\n");
        Path output=Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,sql.toString(),StandardCharsets.UTF_8);
    }
    private static String values(Object... values) {
        return Arrays.stream(values).map(value -> value==null?"NULL":value instanceof Boolean b?(b?"TRUE":"FALSE"):
                value instanceof Number?value.toString():"CONVERT(0x"+HexFormat.of().formatHex(value.toString().getBytes(StandardCharsets.UTF_8))+" USING utf8mb4)")
                .collect(Collectors.joining(","));
    }
}
