package com.amatrix.sic.iam;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;

@Service
public class ExpertQualificationService {
    private static final List<String> ACTIVE = List.of("PENDING_DEPT_REVIEW", "PENDING_SCIENCE_REVIEW");
    private final JdbcTemplate jdbc;
    private final IamRepository users;
    private final StringRedisTemplate redis;
    private final OutboxService outbox;

    public ExpertQualificationService(JdbcTemplate jdbc, IamRepository users, StringRedisTemplate redis,
            OutboxService outbox) {
        this.jdbc = jdbc;
        this.users = users;
        this.redis = redis;
        this.outbox = outbox;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submit(Long actorId, Map<String, Object> request) {
        var actor = requireUser(actorId);
        if (actor.roleCodes().contains("EXPERT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user already has EXPERT role");
        }
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM expert_qualification WHERE user_id=?
                 AND status IN ('PENDING_DEPT_REVIEW','PENDING_SCIENCE_REVIEW')
                """, Integer.class, actorId);
        if (active != null && active > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is already an active expert qualification application");
        }
        String specialty = required(request.get("specialty"), "Specialty is required");
        String reason = required(request.get("applicationReason"), "Application reason is required");
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO expert_qualification(user_id,applicant_dept_id,specialty,professional_title,
                       application_reason,status) VALUES (?,?,?,?,?,'PENDING_DEPT_REVIEW')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, actorId);
            if (actor.deptId() == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, actor.deptId());
            statement.setString(3, specialty);
            statement.setString(4, optional(request.get("professionalTitle")));
            statement.setString(5, reason);
            return statement;
        }, holder);
        Long id = Objects.requireNonNull(holder.getKey()).longValue();
        emit("EXPERT_QUALIFICATION_SUBMITTED", id, actorId, Map.of("applicationId", id, "userId", actorId));
        return application(id);
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> mine(Long actorId) {
        var actor = requireUser(actorId);
        List<Map<String, Object>> applications = applications("q.user_id=?", actorId);
        boolean hasActive = applications.stream().anyMatch(row -> ACTIVE.contains(row.get("status")));
        return linked("expert", actor.roleCodes().contains("EXPERT"), "hasActiveApplication", hasActive,
                "applications", applications);
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_DEPT') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_SCIENCE')")
    public Map<String, Object> query(Long actorId) {
        var actor = requireUser(actorId);
        List<Map<String, Object>> result;
        if (actor.roleCodes().contains("DEPT_ADMIN") && !actor.roleCodes().contains("SYSTEM_ADMIN")
                && !actor.permissionCodes().contains("expert:qualification:review:science")) {
            result = applications("q.applicant_dept_id=?", actor.deptId());
        } else {
            result = applications("1=1");
        }
        return linked("applications", result);
    }

    @Transactional
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_DEPT')")
    public Map<String, Object> departmentReview(Long actorId, Long id, Map<String, Object> request) {
        var actor = requireUser(actorId);
        Map<String, Object> current = application(id);
        if (!"PENDING_DEPT_REVIEW".equals(current.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not pending department review");
        }
        if (!actor.roleCodes().contains("SYSTEM_ADMIN")
                && !Objects.equals(actor.deptId(), current.get("applicantDeptId"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only review applications from your department");
        }
        boolean approved = Boolean.TRUE.equals(request.get("approved"));
        String opinion = required(request.get("opinion"), "Review opinion is required");
        jdbc.update("""
                UPDATE expert_qualification SET dept_reviewer_user_id=?,dept_review_opinion=?,dept_review_remark=?,
                       dept_reviewed_at=?,status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?
                """, actorId, opinion, optional(request.get("remark")), LocalDateTime.now(),
                approved ? "PENDING_SCIENCE_REVIEW" : "DEPT_REJECTED", id);
        emit("EXPERT_QUALIFICATION_REVIEWED", id, actorId,
                linked("applicationId", id, "stage", "DEPARTMENT", "approved", approved));
        return application(id);
    }

    @Transactional
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_SCIENCE')")
    public Map<String, Object> scienceReview(Long actorId, Long id, Map<String, Object> request) {
        Map<String, Object> current = application(id);
        if (!"PENDING_SCIENCE_REVIEW".equals(current.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not pending science office review");
        }
        boolean approved = Boolean.TRUE.equals(request.get("approved"));
        String opinion = required(request.get("opinion"), "Review opinion is required");
        jdbc.update("""
                UPDATE expert_qualification SET science_reviewer_user_id=?,science_review_opinion=?,science_review_remark=?,
                       science_reviewed_at=?,status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?
                """, actorId, opinion, optional(request.get("remark")), LocalDateTime.now(),
                approved ? "APPROVED" : "SCIENCE_REJECTED", id);
        if (approved) {
            Long applicantId = (Long) ((Map<?, ?>) current.get("applicant")).get("userId");
            users.assignRole(applicantId, "EXPERT");
            jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?", applicantId);
            Long version = jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?", Long.class, applicantId);
            redis.opsForValue().set("sic:jwt:version:" + applicantId, String.valueOf(version));
            emit("IAM_USER_ROLES_CHANGED", applicantId, actorId,
                    linked("userId", applicantId, "grantedRole", "EXPERT"));
        }
        emit("EXPERT_QUALIFICATION_REVIEWED", id, actorId,
                linked("applicationId", id, "stage", "SCIENCE", "approved", approved));
        return application(id);
    }

    private Map<String, Object> application(Long id) {
        List<Map<String, Object>> rows = applications("q.id=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Expert qualification application not found");
        return rows.getFirst();
    }

    private List<Map<String, Object>> applications(String predicate, Object... args) {
        String sql = """
                SELECT q.*,d.name applicant_dept_name,
                       au.username applicant_username,au.real_name applicant_real_name,
                       du.username dept_username,du.real_name dept_real_name,
                       su.username science_username,su.real_name science_real_name,
                       (SELECT GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',') FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=q.user_id) applicant_roles,
                       (SELECT GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',') FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=q.dept_reviewer_user_id) dept_roles,
                       (SELECT GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',') FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=q.science_reviewer_user_id) science_roles
                  FROM expert_qualification q LEFT JOIN department d ON d.id=q.applicant_dept_id
                  JOIN app_user au ON au.id=q.user_id LEFT JOIN app_user du ON du.id=q.dept_reviewer_user_id
                  LEFT JOIN app_user su ON su.id=q.science_reviewer_user_id WHERE
                """ + predicate + " ORDER BY q.id DESC";
        return jdbc.query(sql, (rs, row) -> linked(
                "applicationId", rs.getLong("id"),
                "applicant", userSummary(rs.getLong("user_id"), rs.getString("applicant_username"),
                        rs.getString("applicant_real_name"), nullableLong(rs, "applicant_dept_id"),
                        rs.getString("applicant_dept_name"), rs.getString("applicant_roles")),
                "applicantDeptId", nullableLong(rs, "applicant_dept_id"),
                "applicantDeptName", rs.getString("applicant_dept_name"),
                "specialty", rs.getString("specialty"), "professionalTitle", rs.getString("professional_title"),
                "applicationReason", rs.getString("application_reason"), "status", rs.getString("status"),
                "deptReviewer", nullableSummary(rs, "dept_reviewer_user_id", "dept_username", "dept_real_name", "dept_roles"),
                "deptReviewOpinion", rs.getString("dept_review_opinion"),
                "deptReviewRemark", rs.getString("dept_review_remark"),
                "deptReviewedAt", rs.getObject("dept_reviewed_at", LocalDateTime.class),
                "scienceReviewer", nullableSummary(rs, "science_reviewer_user_id", "science_username", "science_real_name", "science_roles"),
                "scienceReviewOpinion", rs.getString("science_review_opinion"),
                "scienceReviewRemark", rs.getString("science_review_remark"),
                "scienceReviewedAt", rs.getObject("science_reviewed_at", LocalDateTime.class),
                "createdAt", rs.getObject("created_at", LocalDateTime.class),
                "updatedAt", rs.getObject("updated_at", LocalDateTime.class)), args);
    }

    private Map<String, Object> nullableSummary(java.sql.ResultSet rs, String idColumn, String usernameColumn,
            String realNameColumn, String rolesColumn) throws java.sql.SQLException {
        Long id = nullableLong(rs, idColumn);
        return id == null ? null : userSummary(id, rs.getString(usernameColumn), rs.getString(realNameColumn),
                null, null, rs.getString(rolesColumn));
    }

    private static Map<String, Object> userSummary(Long id, String username, String realName, Long deptId,
            String deptName, String roleCodes) {
        return linked("userId", id, "username", username, "realName", realName, "deptId", deptId,
                "deptName", deptName, "roleCodes", split(roleCodes));
    }

    private IamDtos.UserAccount requireUser(Long id) {
        var user = users.findById(id);
        if (user == null || !user.enabled()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "User is disabled or missing");
        return user;
    }

    private void emit(String type, Object aggregate, Long actorId, Map<String, Object> payload) {
        outbox.enqueue("iam.changed", EventEnvelope.create(type, "EXPERT_QUALIFICATION", String.valueOf(aggregate),
                null, null, actorId, payload));
    }

    private static String required(Object value, String message) {
        String normalized = optional(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private static String optional(Object value) {
        if (value == null) return null; String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
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
