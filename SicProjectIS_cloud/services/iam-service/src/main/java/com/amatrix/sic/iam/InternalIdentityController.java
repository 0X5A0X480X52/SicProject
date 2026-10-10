package com.amatrix.sic.iam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.IamOverviewStats;
import com.amatrix.sic.contracts.UserSummary;

@RestController
@RequestMapping("/internal")
public class InternalIdentityController {
    private final IamRepository repository;
    private final JwtTokenIssuer tokenIssuer;
    private final ServiceTokenProperties tokenProperties;
    private final JdbcTemplate jdbc;

    public InternalIdentityController(IamRepository repository, JwtTokenIssuer tokenIssuer,
            ServiceTokenProperties tokenProperties, JdbcTemplate jdbc) {
        this.repository = repository;
        this.tokenIssuer = tokenIssuer;
        this.tokenProperties = tokenProperties;
        this.jdbc = jdbc;
    }

    @PostMapping("/tokens/service")
    ApiResponse<JwtTokenIssuer.IssuedToken> serviceToken(
            @RequestHeader("X-Service-Bootstrap-Secret") String secret,
            @RequestParam String serviceName) {
        if (tokenProperties.bootstrapSecret() == null || !MessageDigest.isEqual(
                tokenProperties.bootstrapSecret().getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
        return ApiResponse.ok(tokenIssuer.issueServiceToken(serviceName, List.of("internal")));
    }

    @GetMapping("/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        var user = repository.findById(userId);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return ApiResponse.ok(new UserSummary(user.userId(), user.username(), user.realName(), user.deptId(),
                user.deptName(), user.roleCodes(), user.enabled(), user.authVersion()));
    }

    @GetMapping("/admin/overview-stats")
    ApiResponse<IamOverviewStats> overviewStats() {
        long totalUsers = count("SELECT COUNT(*) FROM app_user");
        long enabledUsers = count("SELECT COUNT(*) FROM app_user WHERE enabled=TRUE");
        long totalRoles = count("SELECT COUNT(*) FROM app_role");
        long totalPermissions = count("SELECT COUNT(*) FROM app_permission");
        List<IamOverviewStats.CountItem> roleCounts = jdbc.query("""
                SELECT r.code,r.name,COUNT(ur.user_id) user_count FROM app_role r
                  LEFT JOIN user_role ur ON ur.role_id=r.id GROUP BY r.id,r.code,r.name ORDER BY r.code
                """, (rs, row) -> new IamOverviewStats.CountItem(rs.getString(1), rs.getString(2), rs.getLong(3)));
        return ApiResponse.ok(new IamOverviewStats(totalUsers, enabledUsers, totalRoles, totalPermissions,
                roleCounts));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
