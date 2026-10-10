package com.amatrix.sic.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;

@Service
public class NoticeService {
    private final JdbcTemplate jdbc;

    public NoticeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list(ActorContext actor, String moduleType) {
        List<Map<String, Object>> notices;
        if (moduleType == null || moduleType.isBlank()) {
            notices = jdbc.query("SELECT * FROM notice_record ORDER BY COALESCE(publish_time,created_at) DESC,id DESC",
                    (rs, row) -> map(rs));
        } else {
            notices = jdbc.query("""
                    SELECT * FROM notice_record WHERE module_type=?
                     ORDER BY COALESCE(publish_time,created_at) DESC,id DESC
                    """, (rs, row) -> map(rs), moduleType.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return notices.stream().filter(notice -> visible(actor, notice)).toList();
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(ActorContext actor, Long noticeId) {
        List<Map<String, Object>> rows = jdbc.query("SELECT * FROM notice_record WHERE id=?",
                (rs, row) -> map(rs), noticeId);
        if (rows.isEmpty() || !visible(actor, rows.getFirst())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
        return rows.getFirst();
    }

    @PreAuthorize("hasRole('SCIENCE_ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Transactional
    public Map<String, Object> create(ActorContext actor, Map<String, Object> request) {
        validate(request);
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> statement(connection.prepareStatement("""
                INSERT INTO notice_record(module_type,notice_type,notice_title,notice_no,publish_unit,publish_user_id,
                  publish_time,notice_scope,target_dept_scope,target_user_scope,project_category,limited_project,
                  limit_count,start_time,deadline_time,material_requirement_summary,content_summary,remark)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS), actor.userId(), request), holder);
        return get(actor, Objects.requireNonNull(holder.getKey()).longValue());
    }

    @PreAuthorize("hasRole('SCIENCE_ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Transactional
    public Map<String, Object> update(Long noticeId, ActorContext actor, Map<String, Object> request) {
        validate(request);
        get(actor, noticeId);
        jdbc.update("""
                UPDATE notice_record SET module_type=?,notice_type=?,notice_title=?,notice_no=?,publish_unit=?,
                  publish_user_id=?,publish_time=?,notice_scope=?,target_dept_scope=?,target_user_scope=?,
                  project_category=?,limited_project=?,limit_count=?,start_time=?,deadline_time=?,
                  material_requirement_summary=?,content_summary=?,remark=? WHERE id=?
                """, string(request, "moduleType").toUpperCase(java.util.Locale.ROOT), string(request, "noticeType"),
                string(request, "noticeTitle"), string(request, "noticeNo"), string(request, "publishUnit"),
                actor.userId(), dateTime(request.get("publishTime")), string(request, "noticeScope"),
                string(request, "targetDeptScope"), string(request, "targetUserScope"),
                string(request, "projectCategory"), bool(request.get("isLimitedProject")),
                integer(request.get("limitCount")), dateTime(request.get("startTime")),
                dateTime(request.get("deadlineTime")), string(request, "materialRequirementSummary"),
                string(request, "contentSummary"), string(request, "remark"), noticeId);
        return get(actor, noticeId);
    }

    private boolean visible(ActorContext actor, Map<String, Object> notice) {
        if (actor != null && (actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN"))) return true;
        String scope = String.valueOf(notice.get("noticeScope"));
        if (scope == null || scope.isBlank() || "null".equalsIgnoreCase(scope) || "ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        String normalized = scope.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("DEPT")) {
            return actor != null && actor.deptId() != null
                    && ids(notice.get("targetDeptScope")).contains(String.valueOf(actor.deptId()));
        }
        if (normalized.contains("USER")) {
            return actor != null && actor.userId() != null
                    && ids(notice.get("targetUserScope")).contains(String.valueOf(actor.userId()));
        }
        return false;
    }

    private Set<String> ids(Object raw) {
        if (raw == null) return Set.of();
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) return Set.of();
        value = value.replace('[', ' ').replace(']', ' ').replace("\"", "");
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .collect(Collectors.toSet());
    }

    private PreparedStatement statement(PreparedStatement statement, Long actorId, Map<String, Object> request)
            throws java.sql.SQLException {
        statement.setString(1, string(request, "moduleType").toUpperCase(java.util.Locale.ROOT));
        statement.setString(2, string(request, "noticeType"));
        statement.setString(3, string(request, "noticeTitle"));
        statement.setString(4, string(request, "noticeNo"));
        statement.setString(5, string(request, "publishUnit"));
        statement.setLong(6, actorId);
        statement.setObject(7, dateTime(request.get("publishTime")));
        statement.setString(8, string(request, "noticeScope"));
        statement.setString(9, string(request, "targetDeptScope"));
        statement.setString(10, string(request, "targetUserScope"));
        statement.setString(11, string(request, "projectCategory"));
        statement.setObject(12, bool(request.get("isLimitedProject")));
        statement.setObject(13, integer(request.get("limitCount")));
        statement.setObject(14, dateTime(request.get("startTime")));
        statement.setObject(15, dateTime(request.get("deadlineTime")));
        statement.setString(16, string(request, "materialRequirementSummary"));
        statement.setString(17, string(request, "contentSummary"));
        statement.setString(18, string(request, "remark"));
        return statement;
    }

    private void validate(Map<String, Object> request) {
        if (request == null || blank(string(request, "moduleType")) || blank(string(request, "noticeType"))
                || blank(string(request, "noticeTitle"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Module type, notice type and title are required");
        }
        LocalDateTime start = dateTime(request.get("startTime"));
        LocalDateTime deadline = dateTime(request.get("deadlineTime"));
        if (start != null && deadline != null && deadline.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notice deadline cannot be before start time");
        }
    }

    private Map<String, Object> map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return linked("noticeId", rs.getLong("id"), "moduleInstanceId", nullableLong(rs, "module_instance_id"),
                "stateRecordId", nullableLong(rs, "state_record_id"), "moduleType", rs.getString("module_type"),
                "noticeType", rs.getString("notice_type"), "noticeTitle", rs.getString("notice_title"), "noticeNo",
                rs.getString("notice_no"), "publishUnit", rs.getString("publish_unit"), "publishUserId",
                rs.getLong("publish_user_id"), "publishTime", rs.getObject("publish_time", LocalDateTime.class),
                "noticeScope", rs.getString("notice_scope"), "targetDeptScope", rs.getString("target_dept_scope"),
                "targetUserScope", rs.getString("target_user_scope"), "projectCategory",
                rs.getString("project_category"), "isLimitedProject", nullableBoolean(rs, "limited_project"),
                "limitCount", nullableInteger(rs, "limit_count"), "startTime",
                rs.getObject("start_time", LocalDateTime.class), "deadlineTime",
                rs.getObject("deadline_time", LocalDateTime.class), "materialRequirementSummary",
                rs.getString("material_requirement_summary"), "contentSummary", rs.getString("content_summary"),
                "remark", rs.getString("remark"), "createdAt", rs.getObject("created_at", LocalDateTime.class),
                "updatedAt", rs.getObject("updated_at", LocalDateTime.class));
    }

    private static String string(Map<String, Object> values, String key) { Object value=values==null?null:values.get(key);return value==null?null:String.valueOf(value); }
    private static Boolean bool(Object value) { return value instanceof Boolean bool?bool:value==null?null:Boolean.valueOf(String.valueOf(value)); }
    private static Integer integer(Object value) { return value instanceof Number number?number.intValue():value==null?null:Integer.valueOf(String.valueOf(value)); }
    private static LocalDateTime dateTime(Object value) { return value instanceof LocalDateTime time?time:value==null||String.valueOf(value).isBlank()?null:LocalDateTime.parse(String.valueOf(value)); }
    private static boolean blank(String value) { return value==null||value.isBlank(); }
    private static Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long value=rs.getLong(column);return rs.wasNull()?null:value;}
    private static Integer nullableInteger(java.sql.ResultSet rs,String column)throws java.sql.SQLException{int value=rs.getInt(column);return rs.wasNull()?null:value;}
    private static Boolean nullableBoolean(java.sql.ResultSet rs,String column)throws java.sql.SQLException{boolean value=rs.getBoolean(column);return rs.wasNull()?null:value;}
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
}
