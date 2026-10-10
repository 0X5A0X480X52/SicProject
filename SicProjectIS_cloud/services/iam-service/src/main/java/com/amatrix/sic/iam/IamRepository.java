package com.amatrix.sic.iam;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.amatrix.sic.iam.IamDtos.UserAccount;

@Repository
public class IamRepository {
    private final JdbcTemplate jdbc;

    public IamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserAccount findByUsername(String username) {
        List<Long> ids = jdbc.query("SELECT id FROM app_user WHERE username = ?", (rs, row) -> rs.getLong(1), username);
        return ids.isEmpty() ? null : findById(ids.getFirst());
    }

    public UserAccount findById(Long userId) {
        List<UserAccount> users = jdbc.query("""
                SELECT u.id, u.username, u.password_hash, u.real_name, u.dept_id, d.name dept_name,
                       u.phone, u.email, u.enabled, u.auth_version
                  FROM app_user u LEFT JOIN department d ON d.id = u.dept_id
                 WHERE u.id = ?
                """, (rs, row) -> new UserAccount(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("real_name"), nullableLong(rs, "dept_id"), rs.getString("dept_name"),
                rs.getString("phone"), rs.getString("email"), rs.getBoolean("enabled"),
                rs.getLong("auth_version"), List.of(), List.of()), userId);
        if (users.isEmpty()) {
            return null;
        }
        UserAccount user = users.getFirst();
        return new UserAccount(user.userId(), user.username(), user.passwordHash(), user.realName(), user.deptId(),
                user.deptName(), user.phone(), user.email(), user.enabled(), user.authVersion(),
                roleCodes(userId), permissionCodes(userId));
    }

    public long insertUser(String username, String passwordHash, String realName, Long deptId, String phone, String email) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app_user(username, password_hash, real_name, dept_id, phone, email, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, realName);
            if (deptId == null) statement.setNull(4, java.sql.Types.BIGINT); else statement.setLong(4, deptId);
            statement.setString(5, phone);
            statement.setString(6, email);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }

    public void assignRole(Long userId, String roleCode) {
        jdbc.update("""
                INSERT IGNORE INTO user_role(user_id, role_id)
                SELECT ?, id FROM app_role WHERE code = ?
                """, userId, roleCode);
    }

    public List<String> roleCodes(Long userId) {
        return jdbc.query("""
                SELECT r.code FROM app_role r JOIN user_role ur ON ur.role_id = r.id
                 WHERE ur.user_id = ? ORDER BY r.code
                """, (rs, row) -> rs.getString(1), userId);
    }

    public List<String> permissionCodes(Long userId) {
        return jdbc.query("""
                SELECT DISTINCT p.code FROM app_permission p
                  JOIN role_permission rp ON rp.permission_id = p.id
                  JOIN user_role ur ON ur.role_id = rp.role_id
                 WHERE ur.user_id = ? ORDER BY p.code
                """, (rs, row) -> rs.getString(1), userId);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
