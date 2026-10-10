package com.amatrix.sic.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.amatrix.sic.contracts.IamOverviewStats;

@Service
public class ProjectAuditService {
    private final JdbcTemplate jdbc;
    private final IamDirectoryClient identities;
    private final InternalTokenProvider tokens;
    public ProjectAuditService(JdbcTemplate jdbc, IamDirectoryClient identities, InternalTokenProvider tokens) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.tokens = tokens;
    }

    @PreAuthorize("hasAuthority('PERM_ADMIN_OVERVIEW')")
    public Map<String, Object> overview() {
        IamOverviewStats iam = identities.overviewStats(tokens.authorization()).data();
        if (iam == null) throw new IllegalStateException("IAM overview statistics are unavailable");
        long grants = count("SELECT COUNT(*) FROM project_access_grant WHERE status='ACTIVE'");
        long logs = count("SELECT COUNT(*) FROM admin_operation_log");
        List<Map<String, Object>> grantCounts = jdbc.query("""
                SELECT grant_role_code,COUNT(*) FROM project_access_grant WHERE status='ACTIVE' GROUP BY grant_role_code
                """, (rs, row) -> linked("key", rs.getString(1), "label", rs.getString(1), "count", rs.getLong(2)));
        List<Map<String,Object>> roleCounts = iam.roleCounts().stream()
                .map(item -> linked("key",item.key(),"label",item.label(),"count",item.count())).toList();
        return linked("totalUsers", iam.totalUsers(), "enabledUsers", iam.enabledUsers(), "totalRoles", iam.totalRoles(),
                "totalPermissions", iam.totalPermissions(), "activeProjectGrants", grants, "auditLogCount", logs,
                "grantTypeCounts", grantCounts, "roleCounts", roleCounts);
    }

    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    public Map<String, Object> logs(String keyword, String actionType, String scopeType, Long projectId,
            Long operatorUserId, LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("SELECT * FROM admin_operation_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (text(keyword)) { sql.append(" AND (remark LIKE ? OR project_name_snapshot LIKE ?)"); args.add("%"+keyword.trim()+"%"); args.add("%"+keyword.trim()+"%"); }
        if (text(actionType)) { sql.append(" AND action_type=?"); args.add(actionType); }
        if (text(scopeType)) { sql.append(" AND scope_type=?"); args.add(scopeType); }
        if (projectId != null) { sql.append(" AND project_id=?"); args.add(projectId); }
        if (operatorUserId != null) { sql.append(" AND operator_user_id=?"); args.add(operatorUserId); }
        if (dateFrom != null) { sql.append(" AND created_at>=?"); args.add(dateFrom.atStartOfDay()); }
        if (dateTo != null) { sql.append(" AND created_at<?"); args.add(dateTo.plusDays(1).atStartOfDay()); }
        sql.append(" ORDER BY created_at DESC LIMIT 1000");
        List<Map<String, Object>> rows = jdbc.query(sql.toString(), (rs, row) -> linked(
                "logId", rs.getString("id"), "scopeType", rs.getString("scope_type"),
                "actionType", rs.getString("action_type"), "projectId", nullableLong(rs, "project_id"),
                "projectName", rs.getString("project_name_snapshot"),
                "operatorUser", summary(nullableLong(rs,"operator_user_id"), rs.getString("operator_name_snapshot")),
                "targetUser", summary(nullableLong(rs,"target_user_id"), rs.getString("target_name_snapshot")),
                "grantType", rs.getString("grant_type"), "roleCode", rs.getString("role_code"),
                "permissionCode", rs.getString("permission_code"), "beforeSnapshot", rs.getString("before_snapshot"),
                "afterSnapshot", rs.getString("after_snapshot"), "remark", rs.getString("remark"),
                "createdAt", rs.getObject("created_at", LocalDateTime.class)), args.toArray());
        return linked("logs", rows);
    }

    private long count(String sql) { Long value = jdbc.queryForObject(sql, Long.class); return value == null ? 0 : value; }
    private static Map<String,Object> summary(Long id,String name) { return id==null?null:linked("userId",id,"username",name,"realName",name); }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long v=rs.getLong(column);return rs.wasNull()?null:v;}
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(String.valueOf(pairs[i]),pairs[i+1]);return m;}
}
