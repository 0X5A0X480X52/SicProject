package com.amatrix.sic.iam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;

@Service
public class IamAdminService {
    private static final List<String> EDITABLE_DEPARTMENT_ROLES =
            List.of("PROJECT_LEADER", "EXPERT", "FINANCE_ADMIN");
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final OutboxService outbox;

    public IamAdminService(JdbcTemplate jdbc, StringRedisTemplate redis, OutboxService outbox) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.outbox = outbox;
    }

    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String, Object> queryUsers(String username, String realName, Long deptId, Boolean enabled,
            String roleCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.real_name, u.dept_id, d.name dept_name, u.phone, u.email, u.enabled,
                       GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') role_codes
                  FROM app_user u LEFT JOIN department d ON d.id = u.dept_id
                  LEFT JOIN user_role ur ON ur.user_id = u.id LEFT JOIN app_role r ON r.id = ur.role_id
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (hasText(username)) { sql.append(" AND u.username LIKE ?"); args.add("%" + username.trim() + "%"); }
        if (hasText(realName)) { sql.append(" AND u.real_name LIKE ?"); args.add("%" + realName.trim() + "%"); }
        if (deptId != null) { sql.append(" AND u.dept_id = ?"); args.add(deptId); }
        if (enabled != null) { sql.append(" AND u.enabled = ?"); args.add(enabled); }
        if (hasText(roleCode)) {
            sql.append(" AND EXISTS (SELECT 1 FROM user_role ur2 JOIN app_role r2 ON r2.id=ur2.role_id WHERE ur2.user_id=u.id AND r2.code=?)");
            args.add(roleCode.trim());
        }
        sql.append(" GROUP BY u.id, u.username, u.real_name, u.dept_id, d.name, u.phone, u.email, u.enabled ORDER BY u.id");
        List<Map<String, Object>> users = jdbc.query(sql.toString(), (rs, row) -> userMap(rs, false), args.toArray());
        return linked("roles", roles(), "users", users);
    }

    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String, Object> userDetail(Long userId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT u.id, u.username, u.real_name, u.dept_id, d.name dept_name, u.phone, u.email, u.enabled,
                       GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') role_codes
                  FROM app_user u LEFT JOIN department d ON d.id = u.dept_id
                  LEFT JOIN user_role ur ON ur.user_id = u.id LEFT JOIN app_role r ON r.id = ur.role_id
                 WHERE u.id=? GROUP BY u.id,u.username,u.real_name,u.dept_id,d.name,u.phone,u.email,u.enabled
                """, (rs, row) -> userMap(rs, true), userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return rows.getFirst();
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public Map<String, Object> updateRoles(Long operatorId, Long userId, List<String> requested) {
        requireUser(userId);
        List<String> normalized = distinct(requested);
        validateCodes("app_role", normalized, "Unknown role code");
        List<String> before = roleCodes(userId);
        if (Objects.equals(operatorId, userId) && before.contains("SYSTEM_ADMIN")
                && !normalized.contains("SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own SYSTEM_ADMIN role");
        }
        jdbc.update("DELETE FROM user_role WHERE user_id=?", userId);
        normalized.forEach(code -> jdbc.update("""
                INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM app_role WHERE code=? AND enabled=TRUE
                """, userId, code));
        invalidateUsers(List.of(userId));
        emit("IAM_USER_ROLES_CHANGED", userId, operatorId,
                linked("userId", userId, "before", before, "after", normalized));
        return linked("user", userDetail(userId), "diff", diff(before, normalized),
                "query", queryUsers(null, null, null, null, null));
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public Map<String, Object> updateStatus(Long operatorId, Long userId, boolean enabled) {
        requireUser(userId);
        if (Objects.equals(operatorId, userId) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own account");
        }
        jdbc.update("UPDATE app_user SET enabled=?, auth_version=auth_version+1 WHERE id=?", enabled, userId);
        cacheVersion(userId);
        emit("IAM_USER_STATUS_CHANGED", userId, operatorId, linked("userId", userId, "enabled", enabled));
        return userDetail(userId);
    }

    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public List<Map<String, Object>> permissions() {
        return jdbc.query("SELECT id,code,name,permission_group,description FROM app_permission ORDER BY code",
                (rs, row) -> linked("permissionId", rs.getLong("id"), "permissionCode", rs.getString("code"),
                        "permissionName", rs.getString("name"), "permissionGroup", rs.getString("permission_group"),
                        "permissionDesc", rs.getString("description")));
    }

    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public Map<String, Object> permissionMatrix() {
        List<Map<String, Object>> matrix = jdbc.query("""
                SELECT r.code,r.name,r.enabled,GROUP_CONCAT(DISTINCT p.code ORDER BY p.code SEPARATOR ',') permission_codes
                  FROM app_role r LEFT JOIN role_permission rp ON rp.role_id=r.id
                  LEFT JOIN app_permission p ON p.id=rp.permission_id
                 GROUP BY r.id,r.code,r.name,r.enabled ORDER BY r.code
                """, (rs, row) -> linked("roleCode", rs.getString("code"), "roleName", rs.getString("name"),
                        "enabled", rs.getBoolean("enabled"),
                        "permissionCodes", split(rs.getString("permission_codes"))));
        return linked("roles", roles(), "permissions", permissions(), "matrix", matrix);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
    public Map<String, Object> updateRolePermissions(Long operatorId, String roleCode, List<String> requested) {
        Long roleId = scalarLong("SELECT id FROM app_role WHERE code=?", roleCode);
        if (roleId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        List<String> normalized = distinct(requested);
        validateCodes("app_permission", normalized, "Unknown permission code");
        List<String> before = jdbc.query("""
                SELECT p.code FROM app_permission p JOIN role_permission rp ON rp.permission_id=p.id
                 WHERE rp.role_id=? ORDER BY p.code
                """, (rs, row) -> rs.getString(1), roleId);
        jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
        normalized.forEach(code -> jdbc.update("""
                INSERT INTO role_permission(role_id,permission_id) SELECT ?,id FROM app_permission WHERE code=?
                """, roleId, code));
        List<Long> affected = jdbc.query("SELECT user_id FROM user_role WHERE role_id=?",
                (rs, row) -> rs.getLong(1), roleId);
        invalidateUsers(affected);
        emit("IAM_ROLE_PERMISSIONS_CHANGED", roleId, operatorId,
                linked("roleCode", roleCode, "before", before, "after", normalized, "affectedUsers", affected));
        return linked("roleCode", roleCode, "diff", diff(before, normalized), "matrix", permissionMatrix());
    }

    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public List<Map<String, Object>> departments(Long operatorId) {
        Long scope = departmentScope(operatorId);
        return jdbc.query("SELECT id,code,name,parent_id,enabled FROM department WHERE (? IS NULL OR id=?) ORDER BY id",
                (rs, row) -> linked("deptId", rs.getLong("id"), "deptCode", rs.getString("code"),
                        "deptName", rs.getString("name"), "parentDeptId", nullableLong(rs, "parent_id"),
                        "enabled", rs.getBoolean("enabled")), scope, scope);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> createDepartment(Long operatorId, Map<String, Object> request) {
        requireGlobalDepartmentManager(operatorId);
        String code = required(request.get("deptCode"), "Department code is required");
        String name = required(request.get("deptName"), "Department name is required");
        Long parentId = asLong(request.get("parentDeptId"));
        boolean enabled = !Boolean.FALSE.equals(request.get("enabled"));
        jdbc.update("INSERT INTO department(code,name,parent_id,enabled) VALUES (?,?,?,?)", code, name, parentId, enabled);
        Long id = scalarLong("SELECT id FROM department WHERE code=?", code);
        emit("IAM_DEPARTMENT_CHANGED", id, operatorId, linked("departmentId", id, "operation", "CREATE"));
        return department(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> updateDepartment(Long operatorId, Long deptId, Map<String, Object> request) {
        requireGlobalDepartmentManager(operatorId);
        requireDepartment(deptId);
        String code = required(request.get("deptCode"), "Department code is required");
        String name = required(request.get("deptName"), "Department name is required");
        Long parentId = asLong(request.get("parentDeptId"));
        if (Objects.equals(parentId, deptId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department cannot be its own parent");
        boolean enabled = !Boolean.FALSE.equals(request.get("enabled"));
        jdbc.update("UPDATE department SET code=?,name=?,parent_id=?,enabled=? WHERE id=?",
                code, name, parentId, enabled, deptId);
        emit("IAM_DEPARTMENT_CHANGED", deptId, operatorId, linked("departmentId", deptId, "operation", "UPDATE"));
        return department(deptId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public void deleteDepartment(Long operatorId, Long deptId) {
        requireGlobalDepartmentManager(operatorId);
        requireDepartment(deptId);
        Integer references = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM app_user WHERE dept_id=?) +
                       (SELECT COUNT(*) FROM department WHERE parent_id=?)
                """, Integer.class, deptId, deptId);
        if (references != null && references > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department is still referenced");
        }
        jdbc.update("DELETE FROM department WHERE id=?", deptId);
        emit("IAM_DEPARTMENT_CHANGED", deptId, operatorId, linked("departmentId", deptId, "operation", "DELETE"));
    }

    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> departmentMembers(Long operatorId, Long deptId) {
        Long scope = departmentScope(operatorId);
        Long selected = scope == null ? deptId : scope;
        if (selected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required");
        }
        requireDepartment(selected);
        List<Map<String, Object>> members = usersForDepartment(selected, null);
        return linked("departments", departments(operatorId), "assignableRoleCodes", EDITABLE_DEPARTMENT_ROLES,
                "members", members, "canManageDepartments", scope == null, "memberManagementDisabled", false,
                "disabledReason", null);
    }

    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> candidates(Long operatorId, String keyword) {
        Long scope = departmentScope(operatorId);
        return linked("users", scope == null ? usersForDepartment(null, keyword)
                : usersForDepartmentCandidates(scope, keyword));
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> upsertMember(Long operatorId, Long deptId, Long userId, List<String> roles) {
        requireManagedDepartment(operatorId, deptId);
        requireDepartment(deptId);
        requireUser(userId);
        Long currentDept = nullableScalarLong("SELECT dept_id FROM app_user WHERE id=?", userId);
        if (!isGlobalDepartmentManager(operatorId) && currentDept != null && !Objects.equals(currentDept, deptId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Department administrators cannot move users from another department");
        }
        jdbc.update("UPDATE app_user SET dept_id=?,auth_version=auth_version+1 WHERE id=?", deptId, userId);
        replaceEditableRoles(userId, roles);
        cacheVersion(userId);
        emit("IAM_USER_CHANGED", userId, operatorId, linked("userId", userId, "deptId", deptId));
        return member(userId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> updateMemberRoles(Long operatorId, Long userId, List<String> roles) {
        requireUser(userId);
        requireManagedDepartment(operatorId, nullableScalarLong("SELECT dept_id FROM app_user WHERE id=?", userId));
        replaceEditableRoles(userId, roles);
        invalidateUsers(List.of(userId));
        emit("IAM_USER_ROLES_CHANGED", userId, operatorId, linked("userId", userId, "roles", roles));
        return member(userId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PERM_DEPARTMENT_MEMBER_MANAGE')")
    public Map<String, Object> removeMember(Long operatorId, Long userId) {
        requireUser(userId);
        requireManagedDepartment(operatorId, nullableScalarLong("SELECT dept_id FROM app_user WHERE id=?", userId));
        jdbc.update("UPDATE app_user SET dept_id=NULL,auth_version=auth_version+1 WHERE id=?", userId);
        cacheVersion(userId);
        emit("IAM_USER_CHANGED", userId, operatorId, linked("userId", userId, "deptId", null));
        return member(userId);
    }

    private List<Map<String, Object>> usersForDepartment(Long deptId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id,u.username,u.real_name,u.dept_id,d.name dept_name,u.phone,u.email,u.enabled,
                       GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') role_codes
                  FROM app_user u LEFT JOIN department d ON d.id=u.dept_id
                  LEFT JOIN user_role ur ON ur.user_id=u.id LEFT JOIN app_role r ON r.id=ur.role_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (deptId != null) { sql.append(" AND u.dept_id=?"); args.add(deptId); }
        if (hasText(keyword)) {
            sql.append(" AND (u.username LIKE ? OR u.real_name LIKE ?)");
            args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%");
        }
        sql.append(" GROUP BY u.id,u.username,u.real_name,u.dept_id,d.name,u.phone,u.email,u.enabled ORDER BY u.id");
        return jdbc.query(sql.toString(), (rs, row) -> memberMap(rs), args.toArray());
    }

    private List<Map<String, Object>> usersForDepartmentCandidates(Long deptId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id,u.username,u.real_name,u.dept_id,d.name dept_name,u.phone,u.email,u.enabled,
                       GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') role_codes
                  FROM app_user u LEFT JOIN department d ON d.id=u.dept_id
                  LEFT JOIN user_role ur ON ur.user_id=u.id LEFT JOIN app_role r ON r.id=ur.role_id
                 WHERE (u.dept_id IS NULL OR u.dept_id=?)
                """);
        List<Object> args = new ArrayList<>(); args.add(deptId);
        if (hasText(keyword)) {
            sql.append(" AND (u.username LIKE ? OR u.real_name LIKE ?)");
            args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%");
        }
        sql.append(" GROUP BY u.id,u.username,u.real_name,u.dept_id,d.name,u.phone,u.email,u.enabled ORDER BY u.id");
        return jdbc.query(sql.toString(), (rs, row) -> memberMap(rs), args.toArray());
    }

    private Map<String, Object> member(Long userId) {
        return usersForDepartment(null, null).stream()
                .filter(user -> Objects.equals(user.get("userId"), userId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void replaceEditableRoles(Long userId, List<String> requested) {
        List<String> normalized = distinct(requested);
        if (!EDITABLE_DEPARTMENT_ROLES.containsAll(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not assignable by department management");
        }
        jdbc.update("""
                DELETE ur FROM user_role ur JOIN app_role r ON r.id=ur.role_id
                 WHERE ur.user_id=? AND r.code IN ('PROJECT_LEADER','EXPERT','FINANCE_ADMIN')
                """, userId);
        normalized.forEach(code -> jdbc.update("""
                INSERT INTO user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code=? AND enabled=TRUE
                """, userId, code));
    }

    private List<Map<String, Object>> roles() {
        return jdbc.query("SELECT id,code,name,description,enabled FROM app_role ORDER BY code",
                (rs, row) -> linked("roleId", rs.getLong("id"), "roleCode", rs.getString("code"),
                        "roleName", rs.getString("name"), "roleDesc", rs.getString("description"),
                        "enabled", rs.getBoolean("enabled")));
    }

    private Map<String, Object> department(Long id) {
        return jdbc.query("SELECT id,code,name,parent_id,enabled FROM department WHERE id=?",
                (rs, row) -> linked("deptId", rs.getLong("id"), "deptCode", rs.getString("code"),
                        "deptName", rs.getString("name"), "parentDeptId", nullableLong(rs, "parent_id"),
                        "enabled", rs.getBoolean("enabled")), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
    }

    private Long departmentScope(Long operatorId) {
        requireUser(operatorId);
        if (isGlobalDepartmentManager(operatorId)) return null;
        Long deptId = nullableScalarLong("SELECT dept_id FROM app_user WHERE id=?", operatorId);
        if (deptId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Department administrator has no department scope");
        return deptId;
    }

    private boolean isGlobalDepartmentManager(Long operatorId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.id=ur.role_id
                 WHERE ur.user_id=? AND r.code IN ('SYSTEM_ADMIN','SCIENCE_ADMIN')
                """, Integer.class, operatorId);
        return count != null && count > 0;
    }

    private void requireGlobalDepartmentManager(Long operatorId) {
        if (!isGlobalDepartmentManager(operatorId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only global administrators can change department structure");
    }

    private void requireManagedDepartment(Long operatorId, Long deptId) {
        if (isGlobalDepartmentManager(operatorId)) return;
        Long scope = departmentScope(operatorId);
        if (deptId == null || !Objects.equals(scope, deptId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Department administrators can only manage their own department");
    }

    private Map<String, Object> userMap(java.sql.ResultSet rs, boolean detail) throws java.sql.SQLException {
        Map<String, Object> row = linked("userId", rs.getLong("id"), "username", rs.getString("username"),
                "realName", rs.getString("real_name"), "deptId", nullableLong(rs, "dept_id"),
                "deptName", rs.getString("dept_name"), "enabled", rs.getBoolean("enabled"),
                "roleCodes", split(rs.getString("role_codes")), "canEditRoles", true,
                "canToggleStatus", true);
        if (detail) {
            row.put("phone", rs.getString("phone")); row.put("email", rs.getString("email"));
        }
        return row;
    }

    private Map<String, Object> memberMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        return linked("userId", rs.getLong("id"), "username", rs.getString("username"),
                "realName", rs.getString("real_name"), "deptId", nullableLong(rs, "dept_id"),
                "deptName", rs.getString("dept_name"), "phone", rs.getString("phone"),
                "email", rs.getString("email"), "enabled", rs.getBoolean("enabled"),
                "roleCodes", split(rs.getString("role_codes")), "editableRoleCodes", EDITABLE_DEPARTMENT_ROLES);
    }

    private List<String> roleCodes(Long userId) {
        return jdbc.query("SELECT r.code FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.code",
                (rs, row) -> rs.getString(1), userId);
    }

    private void invalidateUsers(Collection<Long> userIds) {
        for (Long userId : new LinkedHashSet<>(userIds)) {
            jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?", userId);
            cacheVersion(userId);
        }
    }

    private void cacheVersion(Long userId) {
        Long version = scalarLong("SELECT auth_version FROM app_user WHERE id=?", userId);
        if (version != null) redis.opsForValue().set("sic:jwt:version:" + userId, version.toString());
    }

    private void validateCodes(String table, List<String> codes, String message) {
        for (String code : codes) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE code=?", Integer.class, code);
            if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message + ": " + code);
        }
    }

    private void requireUser(Long id) {
        if (scalarLong("SELECT id FROM app_user WHERE id=?", id) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    private void requireDepartment(Long id) {
        if (scalarLong("SELECT id FROM department WHERE id=?", id) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found");
    }

    private Long scalarLong(String sql, Object... args) {
        List<Long> values = jdbc.query(sql, (rs, row) -> rs.getLong(1), args);
        return values.isEmpty() ? null : values.getFirst();
    }

    private Long nullableScalarLong(String sql, Object... args) {
        List<Long> values = jdbc.query(sql, (rs, row) -> rs.getObject(1, Long.class), args);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void emit(String type, Object aggregateId, Long actorId, Map<String, Object> payload) {
        EventEnvelope<Map<String, Object>> event = EventEnvelope.create(type, "IAM", String.valueOf(aggregateId),
                null, null, actorId, payload);
        outbox.enqueue("iam.changed", event);
    }

    private static Map<String, Object> diff(List<String> before, List<String> after) {
        return linked("added", after.stream().filter(value -> !before.contains(value)).toList(),
                "removed", before.stream().filter(value -> !after.contains(value)).toList());
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(IamAdminService::hasText).map(String::trim).distinct().sorted().toList();
    }

    private static List<String> split(String values) {
        return !hasText(values) ? List.of() : Arrays.stream(values.split(",")).filter(IamAdminService::hasText).toList();
    }

    private static String required(Object value, String message) {
        String string = value == null ? null : String.valueOf(value).trim();
        if (!hasText(string)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return string;
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
