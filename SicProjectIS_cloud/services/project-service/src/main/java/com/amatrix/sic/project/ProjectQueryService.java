package com.amatrix.sic.project;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ProjectQueryService {
    private final JdbcTemplate jdbc;

    public ProjectQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> accessibleProjects(Long userId, Long deptId, List<String> roles) {
        boolean global = roles.contains("SYSTEM_ADMIN") || roles.contains("SCIENCE_ADMIN");
        boolean department = roles.contains("DEPT_ADMIN") && deptId != null;
        boolean expert = roles.contains("EXPERT");
        boolean finance = roles.contains("FINANCE_ADMIN");
        return jdbc.query("""
                SELECT p.id,p.project_code,p.project_name,p.dept_id,p.dept_name_snapshot,p.owner_user_id,
                       p.owner_name_snapshot,p.project_type,p.project_level,p.business_status
                  FROM project p WHERE p.lifecycle_status='ACTIVE' AND (
                       ?=TRUE OR p.owner_user_id=?
                       OR EXISTS (SELECT 1 FROM project_member m WHERE m.project_id=p.id AND m.user_id=?)
                       OR EXISTS (SELECT 1 FROM project_access_grant g WHERE g.project_id=p.id AND g.grantee_user_id=?
                           AND g.status='ACTIVE'
                           AND (g.effective_from IS NULL OR g.effective_from<=CURRENT_TIMESTAMP)
                           AND (g.effective_to IS NULL OR g.effective_to>CURRENT_TIMESTAMP)
                           AND (g.grant_role_code='PROJECT_PROXY_RECORDER_ASSIGNMENT'
                             OR (?=TRUE AND g.grant_role_code='PROJECT_MODULE_EXPERT_ASSIGNMENT')
                             OR (?=TRUE AND g.grant_role_code='PROJECT_FINANCE_HANDLER_ASSIGNMENT')))
                       OR (?=TRUE AND p.dept_id=?)) ORDER BY p.id DESC
                """, (rs, row) -> linked("projectId", rs.getLong("id"),
                        "projectCode", rs.getString("project_code"), "projectName", rs.getString("project_name"),
                        "deptId", nullableLong(rs, "dept_id"), "deptName", rs.getString("dept_name_snapshot"),
                        "leaderUserId", rs.getLong("owner_user_id"), "leaderRealName", rs.getString("owner_name_snapshot"),
                        "projectType", rs.getString("project_type"), "projectLevel", rs.getString("project_level"),
                        "lifecycleStage", rs.getString("business_status")),
                global, userId, userId, userId, expert, finance, department, deptId);
    }

    public static List<String> roles(Object claim) {
        return claim instanceof Collection<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }

    public static Long number(Object claim) {
        return claim instanceof Number number ? number.longValue() : null;
    }

    static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
