package com.amatrix.sic.project;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.UserSummary;

import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectUserDirectory {
    private final JdbcTemplate jdbc;
    private final IamDirectoryClient client;
    private final InternalTokenProvider tokens;
    private final ObjectMapper objectMapper;

    public ProjectUserDirectory(JdbcTemplate jdbc, IamDirectoryClient client, InternalTokenProvider tokens,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc; this.client = client; this.tokens = tokens; this.objectMapper = objectMapper;
    }

    public UserSummary require(Long userId) {
        UserSummary snapshot = find(userId);
        if (snapshot != null) return snapshot;
        var response = client.summary(tokens.authorization(), userId);
        if (!response.success() || response.data() == null) throw new IllegalArgumentException("Selected user does not exist");
        upsert(response.data());
        return response.data();
    }

    public UserSummary refresh(Long userId) {
        var response = client.summary(tokens.authorization(), userId);
        if (!response.success() || response.data() == null) {
            throw new IllegalStateException("IAM user summary is unavailable: " + userId);
        }
        upsert(response.data());
        return response.data();
    }

    public UserSummary find(Long userId) {
        List<UserSummary> rows = jdbc.query("""
                SELECT user_id,username,real_name,dept_id,dept_name,role_codes_json,enabled,source_version
                  FROM user_snapshot WHERE user_id=?
                """, (rs, row) -> new UserSummary(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("real_name"), ProjectQueryService.nullableLong(rs, "dept_id"),
                        rs.getString("dept_name"), readRoles(rs.getString("role_codes_json")),
                        rs.getBoolean("enabled"), rs.getLong("source_version")), userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<UserSummary> allEnabled() {
        return jdbc.query("""
                SELECT user_id,username,real_name,dept_id,dept_name,role_codes_json,enabled,source_version
                  FROM user_snapshot WHERE enabled=TRUE ORDER BY real_name,user_id
                """, (rs, row) -> new UserSummary(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("real_name"), ProjectQueryService.nullableLong(rs, "dept_id"),
                        rs.getString("dept_name"), readRoles(rs.getString("role_codes_json")), true,
                        rs.getLong("source_version")));
    }

    public void upsert(UserSummary user) {
        jdbc.update("""
                INSERT INTO user_snapshot(user_id,username,real_name,dept_id,dept_name,role_codes_json,enabled,source_version)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE username=VALUES(username),real_name=VALUES(real_name),dept_id=VALUES(dept_id),
                    dept_name=VALUES(dept_name),role_codes_json=VALUES(role_codes_json),enabled=VALUES(enabled),
                    source_version=VALUES(source_version)
                """, user.userId(), user.username(), user.realName(), user.deptId(), user.deptName(),
                write(user.roleCodes()), user.enabled(), user.authVersion());
    }

    @SuppressWarnings("unchecked")
    private List<String> readRoles(String json) {
        try { return objectMapper.readValue(json, List.class); }
        catch (Exception ex) { return List.of(); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
