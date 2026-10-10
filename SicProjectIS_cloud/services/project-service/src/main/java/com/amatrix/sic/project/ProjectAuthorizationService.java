package com.amatrix.sic.project;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.UserSummary;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.security.CanAccessProject;

import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectAuthorizationService {
    private static final String EXPERT_GRANT = "PROJECT_MODULE_EXPERT_ASSIGNMENT";
    private static final String FINANCE_GRANT = "PROJECT_FINANCE_HANDLER_ASSIGNMENT";
    private static final String PROXY_GRANT = "PROJECT_PROXY_RECORDER_ASSIGNMENT";
    private final JdbcTemplate jdbc;
    private final ProjectUserDirectory users;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public ProjectAuthorizationService(JdbcTemplate jdbc, ProjectUserDirectory users, OutboxService outbox,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc; this.users = users; this.outbox = outbox; this.objectMapper = objectMapper;
    }

    @CanAccessProject
    public Map<String, Object> detail(Long projectId, ActorContext actor) {
        ProjectRecord project = project(projectId);
        return linked("project", projectSummary(project), "leader", userMap(users.require(project.ownerUserId())),
                "members", members(projectId), "expertGrants", grants(projectId, EXPERT_GRANT),
                "financeGrants", grants(projectId, FINANCE_GRANT), "proxyGrants", grants(projectId, PROXY_GRANT),
                "users", users.allEnabled().stream().map(this::userMap).toList(),
                "capabilities", capabilities(project, actor));
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> changeLeader(Long projectId, ActorContext actor, Long userId, String reason) {
        ProjectRecord project = project(projectId);
        requireCapability(project, actor, "LEADER");
        UserSummary selected = users.require(userId);
        if (!selected.enabled()) throw bad("Selected user is disabled");
        UserSummary previous = users.require(project.ownerUserId());
        jdbc.update("UPDATE project SET owner_user_id=?,owner_name_snapshot=?,version=version+1 WHERE id=?",
                userId, selected.realName(), projectId);
        jdbc.update("UPDATE project_member SET member_role='MEMBER' WHERE project_id=? AND user_id=?",
                projectId, project.ownerUserId());
        upsertMemberRow(projectId, selected, "LEADER", "Project owner");
        audit(project, actor, userId, "CHANGE_LEADER", "PROJECT_LEADER_BINDING", reason,
                write(userMap(previous)), write(userMap(selected)));
        Long projectVersion = jdbc.queryForObject("SELECT version FROM project WHERE id=?", Long.class, projectId);
        emit(projectId, actor.userId(), "PROJECT_LEADER_CHANGED", linked("projectId", projectId,
                "previousUserId", project.ownerUserId(), "userId", userId, "ownerUserId", userId,
                "ownerName", selected.realName(), "deptId", project.deptId(), "projectVersion", projectVersion));
        return detail(projectId, actor);
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> upsertMember(Long projectId, ActorContext actor, Long userId, String responsibility) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, "MEMBER");
        UserSummary selected = users.require(userId);
        upsertMemberRow(projectId, selected, Objects.equals(userId, project.ownerUserId()) ? "LEADER" : "MEMBER",
                normalize(responsibility));
        touch(projectId); audit(project, actor, userId, "UPSERT_MEMBER", "PROJECT_MEMBER_BINDING",
                responsibility, null, write(userMap(selected)));
        emit(projectId, actor.userId(), "PROJECT_MEMBERS_CHANGED", linked("projectId", projectId, "userId", userId));
        return detail(projectId, actor);
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> upsertMembers(Long projectId, ActorContext actor, List<Long> userIds,
            String responsibility) {
        List<Long> ids = distinctIds(userIds);
        for (Long id : ids) upsertMember(projectId, actor, id, responsibility);
        return mutation(detail(projectId, actor), ids.stream().map(String::valueOf).toList(), List.of(),
                ids.size(), "Project members updated");
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> removeMember(Long projectId, ActorContext actor, Long userId) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, "MEMBER");
        if (Objects.equals(project.ownerUserId(), userId)) throw bad("Project leader cannot be removed");
        jdbc.update("DELETE FROM project_member WHERE project_id=? AND user_id=?", projectId, userId);
        touch(projectId); audit(project, actor, userId, "REMOVE_MEMBER", "PROJECT_MEMBER_BINDING", null, null, null);
        emit(projectId, actor.userId(), "PROJECT_MEMBERS_CHANGED", linked("projectId", projectId, "userId", userId));
        return detail(projectId, actor);
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> assignGrant(Long projectId, ActorContext actor, String grantRoleCode,
            Long userId, String moduleType, Integer roundNo, String taskNodeId, String reason) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, capabilityFor(grantRoleCode));
        UserSummary selected = users.require(userId);
        validateSelectedRole(selected, grantRoleCode);
        Integer duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_access_grant WHERE project_id=? AND grantee_user_id=?
                 AND grant_role_code=? AND status='ACTIVE' AND (module_type<=>?) AND (round_no<=>?) AND (task_node_id<=>?)
                """, Integer.class, projectId, userId, grantRoleCode, normalize(moduleType), roundNo, normalize(taskNodeId));
        if (duplicate == null || duplicate == 0) {
            GeneratedKeyHolder holder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO project_access_grant(project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,
                          grant_scope,module_type,round_no,task_node_id,grant_reason,granted_by,granted_by_name_snapshot)
                        VALUES (?,?,?,?,'PROJECT',?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, projectId); ps.setLong(2, userId); ps.setString(3, selected.realName());
                ps.setString(4, grantRoleCode); ps.setString(5, normalize(moduleType));
                if (roundNo == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, roundNo);
                ps.setString(7, normalize(taskNodeId)); ps.setString(8, normalize(reason));
                ps.setLong(9, actor.userId()); ps.setString(10, actor.username()); return ps;
            }, holder);
            touch(projectId); audit(project, actor, userId, "ASSIGN_GRANT", grantRoleCode, reason, null,
                    String.valueOf(holder.getKey()));
            emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED", linked("projectId", projectId,
                    "grantRoleCode", grantRoleCode, "userId", userId));
        }
        return detail(projectId, actor);
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> assignGrants(Long projectId, ActorContext actor, String grantRoleCode,
            List<Long> userIds, String moduleType, Integer roundNo, String taskNodeId, String reason) {
        List<Long> ids = distinctIds(userIds);
        for (Long id : ids) assignGrant(projectId, actor, grantRoleCode, id, moduleType, roundNo, taskNodeId, reason);
        return mutation(detail(projectId, actor), ids.stream().map(String::valueOf).toList(), List.of(),
                ids.size(), "Project grants assigned");
    }

    @Transactional
    public Map<String, Object> syncExpertGrant(Long projectId, ExpertProjectGrantCommand command) {
        if (command == null || !Objects.equals(projectId, command.projectId()) || command.expertUserId() == null) {
            throw bad("Invalid expert project grant command");
        }
        ProjectRecord project = project(projectId);
        UserSummary expert = users.require(command.expertUserId());
        validateSelectedRole(expert, EXPERT_GRANT);
        String moduleType = normalize(command.moduleType());
        String taskNodeId = normalize(command.taskNodeId());
        Integer duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_access_grant WHERE project_id=? AND grantee_user_id=?
                 AND grant_role_code=? AND status='ACTIVE' AND (module_type<=>?) AND (round_no<=>?) AND (task_node_id<=>?)
                """, Integer.class, projectId, command.expertUserId(), EXPERT_GRANT, moduleType,
                command.roundNo(), taskNodeId);
        if (duplicate != null && duplicate > 0) return linked("changed", false, "status", "ALREADY_ACTIVE");
        ActorContext actor = internalActor(command);
        jdbc.update("""
                INSERT INTO project_access_grant(project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,
                  grant_scope,module_type,round_no,task_node_id,grant_reason,granted_by,granted_by_name_snapshot)
                VALUES (?,?,?,?,'MODULE',?,?,?,'Workflow expert assignment',?,?)
                """, projectId, command.expertUserId(), expert.realName(), EXPERT_GRANT, moduleType,
                command.roundNo(), taskNodeId, actor.userId(), actor.username());
        touch(projectId);
        audit(project, actor, command.expertUserId(), "ASSIGN_GRANT", EXPERT_GRANT,
                "Workflow expert assignment", null, taskNodeId);
        emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED",
                linked("projectId", projectId, "grantRoleCode", EXPERT_GRANT, "userId", command.expertUserId()));
        return linked("changed", true, "status", "ACTIVE");
    }

    @Transactional
    public Map<String, Object> revokeExpertGrant(Long projectId, ExpertProjectGrantCommand command) {
        if (command == null || !Objects.equals(projectId, command.projectId()) || command.expertUserId() == null) {
            throw bad("Invalid expert project grant command");
        }
        ProjectRecord project = project(projectId);
        int changed = jdbc.update("""
                UPDATE project_access_grant SET status='REVOKED',effective_to=CURRENT_TIMESTAMP(6)
                 WHERE project_id=? AND grantee_user_id=? AND grant_role_code=? AND status='ACTIVE'
                   AND (module_type<=>?) AND (round_no<=>?) AND (task_node_id<=>?)
                """, projectId, command.expertUserId(), EXPERT_GRANT, normalize(command.moduleType()),
                command.roundNo(), normalize(command.taskNodeId()));
        if (changed > 0) {
            ActorContext actor = internalActor(command);
            touch(projectId);
            audit(project, actor, command.expertUserId(), "REVOKE_GRANT", EXPERT_GRANT,
                    "Workflow expert assignment removed", command.taskNodeId(), null);
            emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED",
                    linked("projectId", projectId, "grantRoleCode", EXPERT_GRANT,
                            "userId", command.expertUserId()));
        }
        return linked("changed", changed > 0, "status", changed > 0 ? "REVOKED" : "NOT_ACTIVE");
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> revoke(Long projectId, ActorContext actor, Long grantId, String reason) {
        ProjectRecord project = project(projectId);
        String role = jdbc.query("SELECT grant_role_code FROM project_access_grant WHERE id=? AND project_id=?",
                (rs, row) -> rs.getString(1), grantId, projectId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project grant not found"));
        requireCapability(project, actor, capabilityFor(role));
        jdbc.update("UPDATE project_access_grant SET status='REVOKED',effective_to=CURRENT_TIMESTAMP(6) WHERE id=?",
                grantId);
        touch(projectId); audit(project, actor, null, "REVOKE_GRANT", role, reason, String.valueOf(grantId), null);
        emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED", linked("projectId", projectId, "grantId", grantId));
        return detail(projectId, actor);
    }

    @Transactional
    @CanAccessProject
    public Map<String, Object> revokeBatch(Long projectId, ActorContext actor, List<Long> grantIds, String reason) {
        List<Long> ids = distinctIds(grantIds);
        for (Long id : ids) revoke(projectId, actor, id, reason);
        return mutation(detail(projectId, actor), List.of(), ids.stream().map(String::valueOf).toList(),
                ids.size(), "Project grants revoked");
    }

    @PreAuthorize("hasAuthority('PERM_PROJECT_GRANT_VIEW')")
    public Map<String, Object> manageableProjects(ActorContext actor, Long deptId) {
        List<Map<String, Object>> projects = jdbc.query("""
                SELECT id,project_code,project_name,dept_id,dept_name_snapshot,owner_user_id,owner_name_snapshot,
                       project_type,project_level,business_status FROM project WHERE lifecycle_status='ACTIVE'
                 AND (?=TRUE OR (?=TRUE AND dept_id=?)) ORDER BY id DESC
                """, (rs, row) -> projectSummary(rowToProject(rs)),
                actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN"), actor.hasRole("DEPT_ADMIN"), deptId);
        List<Map<String, Object>> counts = jdbc.query("""
                SELECT grant_role_code,COUNT(*) count FROM project_access_grant WHERE status='ACTIVE' GROUP BY grant_role_code
                """, (rs, row) -> linked("key", rs.getString(1), "label", rs.getString(1), "count", rs.getLong(2)));
        return linked("projects", projects, "grantTypeCounts", counts);
    }

    private Map<String, Object> mutation(Map<String, Object> detail, List<String> added, List<String> removed,
            int affected, String message) {
        return linked("detail", detail, "diff", linked("added", added, "removed", removed),
                "affectedCount", affected, "summaryMessage", message);
    }

    private List<Map<String, Object>> members(Long projectId) {
        return jdbc.query("""
                SELECT id,user_id,member_role,responsibility,created_at FROM project_member
                 WHERE project_id=? ORDER BY id
                """, (rs, row) -> linked("projectMemberId", rs.getLong("id"), "memberRole", rs.getString("member_role"),
                        "responsibility", rs.getString("responsibility"),
                        "joinedAt", rs.getObject("created_at", LocalDateTime.class),
                        "user", userMap(users.require(rs.getLong("user_id")))), projectId);
    }

    private List<Map<String, Object>> grants(Long projectId, String grantRoleCode) {
        return jdbc.query("""
                SELECT * FROM project_access_grant WHERE project_id=? AND grant_role_code=? ORDER BY id DESC
                """, (rs, row) -> linked("projectRoleGrantId", rs.getLong("id"),
                        "grantRoleCode", rs.getString("grant_role_code"), "grantScope", rs.getString("grant_scope"),
                        "moduleType", rs.getString("module_type"), "roundNo", nullableInt(rs, "round_no"),
                        "taskNodeId", rs.getString("task_node_id"), "status", rs.getString("status"),
                        "grantReason", rs.getString("grant_reason"),
                        "effectiveFrom", rs.getObject("effective_from", LocalDateTime.class),
                        "effectiveTo", rs.getObject("effective_to", LocalDateTime.class),
                        "grantee", userMap(users.require(rs.getLong("grantee_user_id"))),
                        "grantedBy", userMap(users.require(rs.getLong("granted_by")))), projectId, grantRoleCode);
    }

    private void upsertMemberRow(Long projectId, UserSummary user, String memberRole, String responsibility) {
        jdbc.update("""
                INSERT INTO project_member(project_id,user_id,member_role,responsibility,user_name_snapshot)
                VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE member_role=VALUES(member_role),
                    responsibility=VALUES(responsibility),user_name_snapshot=VALUES(user_name_snapshot)
                """, projectId, user.userId(), memberRole, responsibility, user.realName());
    }

    private Map<String, Object> capabilities(ProjectRecord project, ActorContext actor) {
        boolean global = actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN");
        boolean owner = Objects.equals(project.ownerUserId(), actor.userId());
        boolean department = actor.hasRole("DEPT_ADMIN") && Objects.equals(project.deptId(), actorDept(actor));
        return linked("canManageLeader", global, "canManageMembers", global || owner || department,
                "canManageExperts", global || owner || department, "canManageFinance", global,
                "canManageProxy", global || owner);
    }

    private void requireCapability(ProjectRecord project, ActorContext actor, String operation) {
        Map<String, Object> c = capabilities(project, actor);
        String key = switch (operation) {
            case "LEADER" -> "canManageLeader"; case "MEMBER" -> "canManageMembers";
            case "EXPERT" -> "canManageExperts"; case "FINANCE" -> "canManageFinance";
            default -> "canManageProxy";
        };
        if (!Boolean.TRUE.equals(c.get(key))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have permission to manage this project authorization");
    }

    private static String capabilityFor(String grantRoleCode) {
        if (EXPERT_GRANT.equals(grantRoleCode)) return "EXPERT";
        if (FINANCE_GRANT.equals(grantRoleCode)) return "FINANCE";
        return "PROXY";
    }

    private static void validateSelectedRole(UserSummary user, String grantRoleCode) {
        if (EXPERT_GRANT.equals(grantRoleCode) && !user.roleCodes().contains("EXPERT")) throw bad("Selected user does not have global EXPERT role");
        if (FINANCE_GRANT.equals(grantRoleCode) && !user.roleCodes().contains("FINANCE_ADMIN")) throw bad("Selected user does not have global FINANCE_ADMIN role");
    }

    private ProjectRecord project(Long id) {
        List<ProjectRecord> rows = jdbc.query("""
                SELECT id,project_code,project_name,dept_id,dept_name_snapshot,owner_user_id,owner_name_snapshot,
                       project_type,project_level,business_status FROM project WHERE id=? AND lifecycle_status='ACTIVE'
                """, (rs, row) -> rowToProject(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        return rows.getFirst();
    }

    private ProjectRecord rowToProject(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProjectRecord(rs.getLong("id"), rs.getString("project_code"), rs.getString("project_name"),
                ProjectQueryService.nullableLong(rs, "dept_id"), rs.getString("dept_name_snapshot"),
                rs.getLong("owner_user_id"), rs.getString("owner_name_snapshot"), rs.getString("project_type"),
                rs.getString("project_level"), rs.getString("business_status"));
    }

    private Map<String, Object> projectSummary(ProjectRecord p) {
        return linked("projectId", p.id(), "projectCode", p.code(), "projectName", p.name(), "deptId", p.deptId(),
                "deptName", p.deptName(), "leaderUserId", p.ownerUserId(), "leaderRealName", p.ownerName(),
                "projectType", p.type(), "projectLevel", p.level(), "lifecycleStage", p.stage());
    }

    private Map<String, Object> userMap(UserSummary user) {
        return linked("userId", user.userId(), "username", user.username(), "realName", user.realName(),
                "deptId", user.deptId(), "deptName", user.deptName(), "roleCodes", user.roleCodes());
    }

    private void touch(Long projectId) { jdbc.update("UPDATE project SET version=version+1 WHERE id=?", projectId); }

    private void audit(ProjectRecord project, ActorContext actor, Long targetId, String action, String roleCode,
            String remark, String before, String after) {
        UserSummary target = targetId == null ? null : users.require(targetId);
        jdbc.update("""
                INSERT INTO admin_operation_log(id,scope_type,action_type,project_id,project_name_snapshot,
                  operator_user_id,operator_name_snapshot,target_user_id,target_name_snapshot,grant_type,role_code,
                  before_snapshot,after_snapshot,remark) VALUES (?,'PROJECT_AUTHORIZATION',?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), action, project.id(), project.name(), actor.userId(), actor.username(),
                targetId, target == null ? null : target.realName(), roleCode, roleCode, before, after, normalize(remark));
    }

    private void emit(Long projectId, Long actorId, String type, Map<String, Object> payload) {
        outbox.enqueue("project.authorization", EventEnvelope.create(type, "PROJECT", projectId.toString(),
                null, null, actorId, payload));
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static Long actorDept(ActorContext actor) { return actor.deptId(); }

    private static ActorContext internalActor(ExpertProjectGrantCommand command) {
        Long id = command.grantedByUserId() == null ? 0L : command.grantedByUserId();
        String name = normalize(command.grantedByName());
        return new ActorContext(id, name == null ? "workflow-service" : name, null, null,
                List.of(), List.of(), "internal-workflow-grant", 0);
    }

    private static List<Long> distinctIds(List<Long> values) {
        if (values == null || values.isEmpty()) throw bad("At least one user or grant is required");
        return new ArrayList<>(new LinkedHashSet<>(values.stream().filter(Objects::nonNull).toList()));
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value;
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put(String.valueOf(pairs[i]), pairs[i+1]); return result;
    }

    private record ProjectRecord(Long id, String code, String name, Long deptId, String deptName,
            Long ownerUserId, String ownerName, String type, String level, String stage) {
    }
}
