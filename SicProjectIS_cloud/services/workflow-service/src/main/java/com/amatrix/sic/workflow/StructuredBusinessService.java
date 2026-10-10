package com.amatrix.sic.workflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.security.CanAccessProject;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class StructuredBusinessService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowOperationPolicy operationPolicy;

    public StructuredBusinessService(JdbcTemplate jdbc, ObjectMapper objectMapper,
            WorkflowOperationPolicy operationPolicy) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.operationPolicy = operationPolicy;
    }

    @CanAccessProject
    public Map<String, Object> application(Long projectId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id,module_instance_id,project_id,title,limited_project,summary,payload_json,created_at
                  FROM project_application WHERE project_id=? ORDER BY id DESC LIMIT 1
                """, (rs, row) -> {
                    Map<String, Object> payload = read(rs.getString("payload_json"));
                    if (payload.containsKey("application")) return payload;
                    return linked("application", linked("applicationId", rs.getLong("id"), "projectId",
                            rs.getLong("project_id"), "applicationTitle", rs.getString("title"), "isLimitedProject",
                            rs.getBoolean("limited_project"), "applicationSummary", rs.getString("summary"),
                            "createdAt", rs.getObject("created_at", LocalDateTime.class), "updatedAt",
                            rs.getObject("created_at", LocalDateTime.class)), "extension", null, "detail", null);
                }, projectId);
        return rows.isEmpty() ? linked("application", null, "extension", null, "detail", null) : rows.getFirst();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveApplication(Long projectId, ActorContext actor, Map<String, Object> request) {
        Map<String, Object> application = child(request, "application", "Application data is required");
        BusinessTask current = requireBusinessTask(projectId,"APPLICATION","SubmitApplicationTask","APPLICATION_DRAFT",actor);
        List<Long> rows = jdbc.query("""
                SELECT id FROM project_application WHERE project_id=? AND module_instance_id=?
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> rs.getLong(1), projectId, current.moduleId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project application not found");
        Long id = rows.getFirst();
        jdbc.update("""
                UPDATE project_application SET title=COALESCE(?,title),limited_project=COALESCE(?,limited_project),
                  summary=COALESCE(?,summary),payload_json=? WHERE id=?
                """, string(application.get("applicationTitle")), bool(application.get("isLimitedProject")),
                string(application.get("applicationSummary")), write(request), id);
        return application(projectId);
    }

    @CanAccessProject
    public Map<String, Object> contract(Long projectId) {
        return draft(projectId, "CONTRACT", "contract_form", "contract");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveContract(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current = requireBusinessTask(projectId,"CONTRACT","FillContractTask","CONTRACT_DRAFT",actor);
        child(request, "contract", "Contract data is required");
        saveDraft(current, "contract_form", request);
        return contract(projectId);
    }

    @CanAccessProject
    public Map<String, Object> acceptance(Long projectId) {
        return draft(projectId, "ACCEPTANCE", "acceptance_form", "acceptance");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> saveAcceptance(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current = requireBusinessTask(projectId,"ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT",actor);
        child(request, "acceptance", "Acceptance data is required");
        saveDraft(current, "acceptance_form", request);
        return acceptance(projectId);
    }

    @CanAccessProject
    public List<Map<String, Object>> achievements(Long projectId) {
        return jdbc.query("SELECT * FROM project_achievement WHERE project_id=? ORDER BY id",
                (rs, row) -> linked("achievementId", rs.getLong("id"), "projectId", rs.getLong("project_id"),
                        "moduleInstanceId", nullableLong(rs, "module_instance_id"), "acceptanceId",
                        nullableLong(rs, "acceptance_id"), "achievementType", rs.getString("achievement_type"),
                        "achievementTitle", rs.getString("achievement_title"), "authorList",
                        rs.getString("author_list"), "achievementLevel", rs.getString("achievement_level"),
                        "publishOrGrantDate", rs.getObject("publish_or_grant_date", LocalDate.class),
                        "proofMaterialVersionId", nullableLong(rs, "proof_material_version_id"), "remark",
                        rs.getString("remark"), "createdAt", rs.getObject("created_at", LocalDateTime.class),
                        "updatedAt", rs.getObject("updated_at", LocalDateTime.class)), projectId);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> addAchievement(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current=requireBusinessTask(projectId,"ACCEPTANCE","SubmitAcceptanceMaterialsTask","ACCEPTANCE_MATERIAL_DRAFT",actor);
        String type = required(string(value(request, "achievementType")), "Achievement type is required");
        String title = required(string(value(request, "achievementTitle")), "Achievement title is required");
        Long moduleId = currentModuleId(request, current);
        requireRelatedAcceptance(number(value(request, "acceptanceId")), moduleId);
        LocalDate date = date(value(request, "publishOrGrantDate"));
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO project_achievement(project_id,module_instance_id,acceptance_id,achievement_type,
                      achievement_title,author_list,achievement_level,publish_or_grant_date,proof_material_version_id,
                      remark) VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, projectId); statement.setObject(2, moduleId);
            statement.setObject(3, number(value(request, "acceptanceId"))); statement.setString(4, type);
            statement.setString(5, title); statement.setString(6, string(value(request, "authorList")));
            statement.setString(7, string(value(request, "achievementLevel"))); statement.setObject(8, date);
            statement.setObject(9, number(value(request, "proofMaterialVersionId")));
            statement.setString(10, string(value(request, "remark"))); return statement;
        }, holder);
        Long id = Objects.requireNonNull(holder.getKey()).longValue();
        return achievements(projectId).stream().filter(row -> id.equals(row.get("achievementId"))).findFirst()
                .orElseThrow();
    }

    @CanAccessProject
    public List<Map<String, Object>> settlements(Long projectId) {
        return jdbc.query("SELECT * FROM acceptance_financial_settlement WHERE project_id=? ORDER BY id",
                (rs, row) -> linked("settlementId", rs.getLong("id"), "acceptanceId",
                        nullableLong(rs, "acceptance_id"), "projectId", rs.getLong("project_id"),
                        "moduleInstanceId", rs.getLong("module_instance_id"), "stateRecordId",
                        nullableLong(rs, "state_record_id"), "approvedAmount", rs.getBigDecimal("approved_amount"),
                        "receivedAmount", rs.getBigDecimal("received_amount"), "spentAmount",
                        rs.getBigDecimal("spent_amount"), "surplusAmount", rs.getBigDecimal("surplus_amount"),
                        "executionRate", rs.getBigDecimal("execution_rate"), "settlementResult",
                        rs.getString("settlement_result"), "financeOperatorId", rs.getLong("finance_operator_id"),
                        "financeReviewComment", rs.getString("finance_review_comment"), "settledAt",
                        rs.getObject("settled_at", LocalDateTime.class), "createdAt",
                        rs.getObject("created_at", LocalDateTime.class), "updatedAt",
                        rs.getObject("updated_at", LocalDateTime.class)), projectId);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String, Object> addSettlement(Long projectId, ActorContext actor, Map<String, Object> request) {
        BusinessTask current=requireBusinessTask(projectId,"ACCEPTANCE","FinancialSettlementTask","ACCEPTANCE_FINANCIAL_SETTLEMENT",actor);
        Long moduleId = currentModuleId(request, current);
        requireRelatedAcceptance(number(value(request, "acceptanceId")), moduleId);
        requireStateRecord(number(value(request, "stateRecordId")), moduleId, current.round());
        BigDecimal received = decimal(value(request, "receivedAmount"));
        BigDecimal spent = decimal(value(request, "spentAmount"));
        if (received == null || spent == null) throw bad("Module, received amount and spent amount are required");
        if (received.signum() < 0 || spent.signum() < 0) throw bad("Financial amounts cannot be negative");
        BigDecimal surplus = received.subtract(spent);
        BigDecimal rate = received.signum() == 0 ? BigDecimal.ZERO
                : spent.multiply(new BigDecimal("100")).divide(received, 2, RoundingMode.HALF_UP);
        LocalDateTime settledAt = dateTime(value(request, "settledAt"));
        if (settledAt == null) settledAt = LocalDateTime.now();
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        LocalDateTime finalSettledAt = settledAt;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO acceptance_financial_settlement(acceptance_id,project_id,module_instance_id,
                      state_record_id,approved_amount,received_amount,spent_amount,surplus_amount,execution_rate,
                      settlement_result,finance_operator_id,finance_review_comment,settled_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, number(value(request, "acceptanceId"))); statement.setObject(2, projectId);
            statement.setObject(3, moduleId); statement.setObject(4, number(value(request, "stateRecordId")));
            statement.setObject(5, decimal(value(request, "approvedAmount"))); statement.setObject(6, received);
            statement.setObject(7, spent); statement.setObject(8, surplus); statement.setObject(9, rate);
            statement.setString(10, string(value(request, "settlementResult")));
            statement.setLong(11, actor.userId());
            statement.setString(12, string(value(request, "financeReviewComment")));
            statement.setObject(13, finalSettledAt); return statement;
        }, holder);
        Long id = Objects.requireNonNull(holder.getKey()).longValue();
        return settlements(projectId).stream().filter(row -> id.equals(row.get("settlementId"))).findFirst()
                .orElseThrow();
    }

    private Map<String, Object> draft(Long projectId, String moduleType, String table, String rootName) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT f.id,f.payload_json FROM %s f JOIN module_instance m ON m.id=f.module_instance_id
                 WHERE m.project_id=? AND m.module_code=? ORDER BY f.round_no DESC,f.id DESC LIMIT 1
                """.formatted(table), (rs, row) -> read(rs.getString("payload_json")), projectId, moduleType);
        return rows.isEmpty() ? linked(rootName, null, "extension", null) : rows.getFirst();
    }

    private void saveDraft(BusinessTask module, String table, Map<String, Object> request) {
        jdbc.update(("""
                INSERT INTO %s(module_instance_id,round_no,payload_json) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE payload_json=VALUES(payload_json)
                """).formatted(table), module.moduleId(), module.round(), write(request));
    }

    private ModuleRound module(Long projectId, String moduleType) {
        List<ModuleRound> rows = jdbc.query("""
                SELECT id,current_round FROM module_instance WHERE project_id=? AND module_code=?
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> new ModuleRound(rs.getLong(1), rs.getInt(2)), projectId, moduleType);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                moduleType + " module instance not found");
        return rows.getFirst();
    }

    private void requireModuleProject(Long moduleId, Long projectId, boolean required) {
        if (moduleId == null) {
            if (required) throw bad("Module, received amount and spent amount are required");
            return;
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM module_instance WHERE id=? AND project_id=?",
                Integer.class, moduleId, projectId);
        if (count == null || count == 0) throw bad("Module instance does not belong to the project");
    }

    private Long currentModuleId(Map<String, Object> request, BusinessTask current) {
        Long requested = number(value(request, "moduleInstanceId"));
        if (requested != null && !requested.equals(current.moduleId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Business data module does not match the current workflow task");
        }
        return current.moduleId();
    }

    private void requireRelatedAcceptance(Long acceptanceId, Long moduleId) {
        if (acceptanceId == null) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM acceptance_form WHERE id=? AND module_instance_id=?",
                Integer.class, acceptanceId, moduleId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Acceptance record does not belong to the current workflow module");
        }
    }

    private void requireStateRecord(Long stateRecordId, Long moduleId, int round) {
        if (stateRecordId == null) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM state_record WHERE id=? AND module_instance_id=? AND round_no=?
                """, Integer.class, stateRecordId, moduleId, round);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "State record does not belong to the current workflow round");
        }
    }

    private BusinessTask requireBusinessTask(Long projectId,String moduleType,String nodeId,String state,ActorContext actor){
        List<BusinessTask> rows=jdbc.query("""
                SELECT id,current_round FROM module_instance WHERE project_id=? AND module_code=?
                  AND current_node_id=? AND current_state=? AND finished=FALSE ORDER BY id DESC LIMIT 1 FOR UPDATE
                """,(rs,row)->new BusinessTask(rs.getLong("id"),rs.getInt("current_round")),projectId,moduleType,nodeId,state);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Business data does not match the current workflow node");
        BusinessTask task=rows.getFirst();
        if(!operationPolicy.exactCanOperate(actor,task.moduleId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current task cannot be operated by this user");
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> request, String key, String message) {
        if (request == null || !(request.get(key) instanceof Map<?, ?> value)) throw bad(message);
        return (Map<String, Object>) value;
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read structured business data", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to save structured business data", ex);
        }
    }

    private static Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw bad(message);
        return value;
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Boolean bool(Object value) { return value instanceof Boolean bool ? bool : value == null ? null : Boolean.valueOf(String.valueOf(value)); }
    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(String.valueOf(value)); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal decimal ? decimal : value instanceof Number number ? new BigDecimal(number.toString()) : value == null || String.valueOf(value).isBlank() ? null : new BigDecimal(String.valueOf(value)); }
    private static LocalDate date(Object value) { return value instanceof LocalDate date ? date : value == null || String.valueOf(value).isBlank() ? null : LocalDate.parse(String.valueOf(value)); }
    private static LocalDateTime dateTime(Object value) { return value instanceof LocalDateTime date ? date : value == null || String.valueOf(value).isBlank() ? null : LocalDateTime.parse(String.valueOf(value)); }
    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long value=rs.getLong(column); return rs.wasNull()?null:value; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
    private record ModuleRound(Long id, int round) {}
    private record BusinessTask(Long moduleId,int round){}
}
