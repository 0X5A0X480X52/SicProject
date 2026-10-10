package com.amatrix.sic.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
public class WorkflowDefinitionService {
    private final JdbcTemplate jdbc;
    private final FlowableBpmnDefinitionParser parser;
    private final WorkflowAssetInitializer assets;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionService(JdbcTemplate jdbc, FlowableBpmnDefinitionParser parser,
            WorkflowAssetInitializer assets, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.assets = assets;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_UPLOAD')")
    @Transactional
    public Map<String, Object> upload(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) throw bad("BPMN XML is required");
        WorkflowBpmnParseResult model = parser.parse(bpmnXml);
        Validation validation = validateModel(model);
        if (!validation.errors().isEmpty()) throw bad(String.join("; ", validation.errors()));
        String moduleType = model.processConfig().moduleType();
        int version = Objects.requireNonNull(model.processConfig().versionNo());
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO workflow_definition(code,name,version,status,published,bpmn_xml,definition_json)
                        VALUES (?,?,?,'DRAFT',FALSE,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, moduleType);
                statement.setString(2, Objects.requireNonNullElse(model.processConfig().processName(), moduleType));
                statement.setInt(3, version);
                statement.setString(4, bpmnXml);
                statement.setString(5, write(model));
                return statement;
            }, holder);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow definition version already exists: " + moduleType + " v" + version, ex);
        }
        Long id = Objects.requireNonNull(holder.getKey()).longValue();
        assets.persistModel(id, model);
        return summary(row(id), model);
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public List<Map<String, Object>> listAssets() {
        return WorkflowAssetInitializer.ASSET_NAMES.stream().map(assetName -> {
            WorkflowBpmnParseResult model = parser.parse(WorkflowAssetInitializer.read(assetName));
            return linked("assetName", assetName, "resourcePath", WorkflowAssetInitializer.BASE_PATH + assetName,
                    "processKey", model.processConfig().processKey(), "moduleType",
                    model.processConfig().moduleType(), "versionNo", model.processConfig().versionNo());
        }).toList();
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_UPLOAD')")
    public Map<String, Object> uploadAsset(String assetName) {
        requireAsset(assetName);
        return upload(WorkflowAssetInitializer.read(assetName));
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_PUBLISH')")
    @Transactional
    public Map<String, Object> publishAsset(String assetName) {
        requireAsset(assetName);
        WorkflowBpmnParseResult assetModel = parser.parse(WorkflowAssetInitializer.read(assetName));
        List<Long> existing = jdbc.query("SELECT id FROM workflow_definition WHERE code=? AND version=?",
                (rs, row) -> rs.getLong(1), assetModel.processConfig().moduleType(),
                assetModel.processConfig().versionNo());
        Long id;
        if (existing.isEmpty()) {
            id = ((Number) Objects.requireNonNull(upload(WorkflowAssetInitializer.read(assetName))
                    .get("workflowDefinitionId"))).longValue();
        } else {
            id = existing.getFirst();
        }
        return publish(id);
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VALIDATE')")
    public Map<String, Object> validate(Long id) {
        WorkflowBpmnParseResult model = model(id);
        return validationMap(model, validateModel(model));
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_PUBLISH')")
    @Transactional
    public Map<String, Object> publish(Long id) {
        DefinitionRow definition = row(id);
        WorkflowBpmnParseResult model = parser.parse(definition.bpmnXml());
        Validation validation = validateModel(model);
        if (!validation.errors().isEmpty()) throw bad(String.join("; ", validation.errors()));
        jdbc.update("""
                UPDATE workflow_definition SET status='INACTIVE',published=FALSE
                 WHERE code=? AND id<>? AND published=TRUE
                """, model.processConfig().moduleType(), id);
        jdbc.update("""
                UPDATE workflow_definition SET code=?,name=?,version=?,status='ACTIVE',published=TRUE,
                  definition_json=? WHERE id=?
                """, model.processConfig().moduleType(), model.processConfig().processName(),
                model.processConfig().versionNo(), write(model), id);
        assets.persistModel(id, model);
        return detail(id);
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public List<Map<String, Object>> list() {
        return jdbc.query("SELECT * FROM workflow_definition ORDER BY id",
                (rs, row) -> {
                    DefinitionRow definition = definitionRow(rs);
                    return summary(definition, parser.parse(definition.bpmnXml()));
                });
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public Map<String, Object> latest(String moduleType) {
        List<DefinitionRow> rows = jdbc.query("""
                SELECT * FROM workflow_definition WHERE code=? AND published=TRUE ORDER BY version DESC,id DESC LIMIT 1
                """, (rs, row) -> definitionRow(rs), normalizeModule(moduleType));
        if (rows.isEmpty()) throw notFound();
        DefinitionRow definition = rows.getFirst();
        return summary(definition, parser.parse(definition.bpmnXml()));
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public Map<String, Object> detail(Long id) {
        DefinitionRow definition = row(id);
        WorkflowBpmnParseResult model = parser.parse(definition.bpmnXml());
        return linked("definition", summary(definition, model), "validation",
                validationMap(model, validateModel(model)), "nodes", nodeMaps(model), "transitions",
                transitionMaps(model));
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public Map<String, Object> bpmn(Long id) {
        DefinitionRow definition = row(id);
        return linked("workflowDefinitionId", definition.id(), "bpmnXml", definition.bpmnXml());
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public List<Map<String, Object>> nodes(Long id) {
        return nodeMaps(model(id));
    }

    @PreAuthorize("hasAuthority('PERM_WORKFLOW_DEFINITION_VIEW')")
    public List<Map<String, Object>> transitions(Long id) {
        return transitionMaps(model(id));
    }

    private List<Map<String, Object>> nodeMaps(WorkflowBpmnParseResult model) {
        return model.nodes().stream().map(node -> linked("nodeId", node.nodeId(), "nodeName", node.nodeName(),
                "nodeType", node.nodeType(), "stateCode", node.stateCode(), "laneName", node.laneName(),
                "responsibleActorCode", node.responsibleActorCode(), "responsibleActorName",
                node.responsibleActorName(), "candidateRoleCode", node.candidateRoleCode(), "operationMode",
                node.operationMode(), "representedActorCode", node.representedActorCode(), "representedActorName",
                node.representedActorName(), "materialRequirements", node.materialRequirements(), "documentConfigs",
                node.documentConfigs())).toList();
    }

    private List<Map<String, Object>> transitionMaps(WorkflowBpmnParseResult model) {
        return model.transitions().stream().map(transition -> linked("transitionId", transition.transitionId(),
                "sourceRef", transition.sourceRef(), "targetRef", transition.targetRef(), "sourceStateCode",
                transition.sourceStateCode(), "targetStateCode", transition.targetStateCode(), "eventType",
                transition.eventType(), "result", transition.result(), "conditionType", transition.conditionType(),
                "conditionKey", transition.conditionKey(), "conditionValue", transition.conditionValue(),
                "conditionHandlerKey", transition.conditionHandlerKey(), "conditionExpression",
                transition.conditionExpression(), "actionKeys", transition.actionKeys())).toList();
    }

    private Map<String, Object> summary(DefinitionRow definition, WorkflowBpmnParseResult model) {
        return linked("workflowDefinitionId", definition.id(), "processKey", model.processConfig().processKey(),
                "processName", model.processConfig().processName(), "moduleType", model.processConfig().moduleType(),
                "versionNo", model.processConfig().versionNo(), "status", definition.status());
    }

    private Map<String, Object> validationMap(WorkflowBpmnParseResult model, Validation validation) {
        return linked("valid", validation.errors().isEmpty(), "processKey", model.processConfig().processKey(),
                "processName", model.processConfig().processName(), "moduleType", model.processConfig().moduleType(),
                "versionNo", model.processConfig().versionNo(), "nodeCount", model.nodes().size(), "transitionCount",
                model.transitions().size(), "roleCodes", validation.roleCodes(), "stateCodes",
                validation.stateCodes(), "errors", validation.errors(), "warnings", validation.warnings());
    }

    private Validation validateModel(WorkflowBpmnParseResult model) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> states = new LinkedHashSet<>();
        Set<String> roles = new LinkedHashSet<>();
        required(model.processConfig().processKey(), "processConfig.processKey is required", errors);
        required(model.processConfig().moduleType(), "processConfig.moduleType is required", errors);
        if (model.processConfig().versionNo() == null) errors.add("processConfig.versionNo is required");
        for (WorkflowBpmnParseResult.NodeConfig node : model.nodes()) {
            if (blank(node.nodeId()) || !nodeIds.add(node.nodeId())) errors.add("Duplicate or missing nodeId: " + node.nodeId());
            if (!blank(node.stateCode()) && !states.add(node.stateCode())) errors.add("Duplicate stateCode: " + node.stateCode());
            if (!blank(node.candidateRoleCode())) roles.add(node.candidateRoleCode());
            if (!"GATEWAY".equals(node.nodeType()) && blank(node.stateCode())) errors.add("Node has no stateCode: " + node.nodeId());
        }
        Set<String> transitionIds = new HashSet<>();
        for (WorkflowBpmnParseResult.TransitionConfig transition : model.transitions()) {
            if (blank(transition.transitionId()) || !transitionIds.add(transition.transitionId())) errors.add("Duplicate or missing transitionId: " + transition.transitionId());
            if (!nodeIds.contains(transition.sourceRef())) errors.add("Unknown transition sourceRef: " + transition.sourceRef());
            if (!nodeIds.contains(transition.targetRef())) errors.add("Unknown transition targetRef: " + transition.targetRef());
            required(transition.eventType(), "Transition eventType is required: " + transition.transitionId(), errors);
        }
        long starts = model.nodes().stream().filter(node -> "START_EVENT".equals(node.nodeType())).count();
        long ends = model.nodes().stream().filter(node -> "END_EVENT".equals(node.nodeType())).count();
        if (starts != 1) errors.add("Workflow must contain exactly one start event");
        if (ends == 0) errors.add("Workflow must contain at least one end event");
        for (String role : roles) {
            if (!Set.of("SYSTEM_ADMIN", "SCIENCE_ADMIN", "DEPT_ADMIN", "PROJECT_LEADER", "EXPERT", "FINANCE_ADMIN").contains(role)) {
                warnings.add("Role is not part of the standard IAM role set: " + role);
            }
        }
        return new Validation(List.copyOf(roles), List.copyOf(states), List.copyOf(errors), List.copyOf(warnings));
    }

    private WorkflowBpmnParseResult model(Long id) {
        return parser.parse(row(id).bpmnXml());
    }

    private DefinitionRow row(Long id) {
        List<DefinitionRow> rows = jdbc.query("SELECT * FROM workflow_definition WHERE id=?",
                (rs, row) -> definitionRow(rs), id);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    private DefinitionRow definitionRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DefinitionRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getInt("version"), rs.getString("status"), rs.getBoolean("published"),
                rs.getString("bpmn_xml"));
    }

    private void requireAsset(String assetName) {
        if (!WorkflowAssetInitializer.ASSET_NAMES.contains(assetName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow asset not found");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize workflow model", ex);
        }
    }

    private static String normalizeModule(String value) {
        if (blank(value)) throw bad("Module type is required");
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void required(String value, String message, List<String> errors) {
        if (blank(value)) errors.add(message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }

    private record DefinitionRow(Long id, String code, String name, int version, String status, boolean published,
            String bpmnXml) {
    }

    private record Validation(List<String> roleCodes, List<String> stateCodes, List<String> errors,
            List<String> warnings) {
    }
}
