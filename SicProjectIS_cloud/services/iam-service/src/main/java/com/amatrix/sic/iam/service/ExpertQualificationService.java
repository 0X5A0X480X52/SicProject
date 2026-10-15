package com.amatrix.sic.iam.service;

import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.dto.IamDtos;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.iam.dao.QualificationMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.messaging.OutboxService;

/**
 * 专家资格分阶段申请审核，校验重复/有效申请及部门范围；科技处通过后赋 EXPERT、更新授权版本及发布资格/用户变更事件。
 */
@Service
public class ExpertQualificationService {
    private static final List<String> ACTIVE = List.of("PENDING_DEPT_REVIEW", "PENDING_SCIENCE_REVIEW");
    private final QualificationMapper mapper;
    private final IamRepository users;
    private final SessionVersionService versions;
    private final OutboxService outbox;

    public ExpertQualificationService(QualificationMapper mapper, IamRepository users, SessionVersionService versions,
            OutboxService outbox) {
        this.mapper = mapper;
        this.users = users;
        this.versions = versions;
        this.outbox = outbox;
    }

    /**
     * 提交专家资格申请，校验当前用户是否已经是专家或有未完成的申请，锁定当前用户的申请，防止并发提交。
     * 填充申请信息并插入数据库，发布 EXPERT_QUALIFICATION_SUBMITTED 事件。
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submit(Long actorId, Map<String, Object> request) {
        var actor = requireUser(actorId);
        // 校验当前用户是否已经是专家，如果是则不允许重复申请
        if (actor.roleCodes().contains("EXPERT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user already has EXPERT role");
        }
        // 锁定当前用户的申请，防止并发提交
        mapper.lockApplicant(actorId);
        // 查询当前用户是否有未完成的申请，如果有则不允许重复提交
        Integer active = mapper.active(actorId);
        if (active != null && active > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is already an active expert qualification application");
        }
        // 填充申请信息并插入数据库
        String specialty = required(request.get("specialty"), "Specialty is required");
        String reason = required(request.get("applicationReason"), "Application reason is required");
        var insert=linked("userId",actorId,"departmentId",actor.deptId(),"specialty",specialty,
                "professionalTitle",optional(request.get("professionalTitle")),"reason",reason);
        mapper.insert(insert);
        Long id=((Number)Objects.requireNonNull(insert.get("id"))).longValue();
        emit("EXPERT_QUALIFICATION_SUBMITTED", id, actorId, Map.of("applicationId", id, "userId", actorId));
        return application(id);
    }

    /** 
     * 查询当前用户的专家资格申请及状态，返回是否有未完成的申请及申请列表。
     */
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> mine(Long actorId) {
        var actor = requireUser(actorId);
        List<Map<String, Object>> applications = applications(linked("userId",actorId));
        boolean hasActive = applications.stream().anyMatch(row -> ACTIVE.contains(row.get("status")));
        return linked("expert", actor.roleCodes().contains("EXPERT"), "hasActiveApplication", hasActive,
                "applications", applications);
    }

    /** 
     * 查询所有专家资格申请，部门管理员只能查询本部门的申请，系统管理员和科技处可以查询所有申请。
     */
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_DEPT') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_SCIENCE')")
    public Map<String, Object> query(Long actorId) {
        var actor = requireUser(actorId);
        List<Map<String, Object>> result;
        // 部门管理员只能查询本部门的申请，系统管理员和科技处可以查询所有申请
        if (actor.roleCodes().contains("DEPT_ADMIN") && !actor.roleCodes().contains("SYSTEM_ADMIN")
                && !actor.permissionCodes().contains("expert:qualification:review:science")) {
            result = applications(linked("departmentId",actor.deptId(),"departmentOnly",true));
        } else {
            result = applications(Map.of());
        }
        return linked("applications", result);
    }

    // 部门审批
    @Transactional(isolation=Isolation.READ_COMMITTED)
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
        if(mapper.departmentReview(id,actorId,opinion,optional(request.get("remark")),
                approved ? "PENDING_SCIENCE_REVIEW" : "DEPT_REJECTED")!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Application was already reviewed");
        emit("EXPERT_QUALIFICATION_REVIEWED", id, actorId,
                linked("applicationId", id, "stage", "DEPARTMENT", "approved", approved));
        return application(id);
    }

    // 科技处审批
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('PERM_EXPERT_QUALIFICATION_REVIEW_SCIENCE')")
    public Map<String, Object> scienceReview(Long actorId, Long id, Map<String, Object> request) {
        Map<String, Object> current = application(id);
        if (!"PENDING_SCIENCE_REVIEW".equals(current.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not pending science office review");
        }
        boolean approved = Boolean.TRUE.equals(request.get("approved"));
        String opinion = required(request.get("opinion"), "Review opinion is required");
        if(mapper.scienceReview(id,actorId,opinion,optional(request.get("remark")),
                approved ? "APPROVED" : "SCIENCE_REJECTED")!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Application was already reviewed");
        if (approved) {
            Long applicantId = (Long) ((Map<?, ?>) current.get("applicant")).get("userId");
            users.assignRole(applicantId, "EXPERT");
            mapper.incrementVersion(applicantId);
            Long version = mapper.version(applicantId);
            versions.publishAfterCommit(applicantId, version);
            emit("IAM_USER_ROLES_CHANGED", applicantId, actorId,
                    linked("userId", applicantId, "grantedRole", "EXPERT"));
        }
        emit("EXPERT_QUALIFICATION_REVIEWED", id, actorId,
                linked("applicationId", id, "stage", "SCIENCE", "approved", approved));
        return application(id);
    }

    // 查询申请
    private Map<String, Object> application(Long id) {
        List<Map<String, Object>> rows = applications(linked("id",id));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Expert qualification application not found");
        return rows.getFirst();
    }

    // 查询申请列表
    private List<Map<String,Object>> applications(Map<String,Object> filter) {
        return mapper.applications(filter).stream().map(row -> linked(
                "applicationId",number(row,"id"),
                "applicant",userSummary(number(row,"user_id"),(String)row.get("applicant_username"),
                        (String)row.get("applicant_real_name"),number(row,"applicant_dept_id"),
                        (String)row.get("applicant_dept_name"),(String)row.get("applicant_roles")),
                "applicantDeptId",number(row,"applicant_dept_id"),"applicantDeptName",row.get("applicant_dept_name"),
                "specialty",row.get("specialty"),"professionalTitle",row.get("professional_title"),
                "applicationReason",row.get("application_reason"),"status",row.get("status"),
                "deptReviewer",nullableSummary(row,"dept_reviewer_user_id","dept_username","dept_real_name","dept_roles"),
                "deptReviewOpinion",row.get("dept_review_opinion"),"deptReviewRemark",row.get("dept_review_remark"),
                "deptReviewedAt",time(row,"dept_reviewed_at"),
                "scienceReviewer",nullableSummary(row,"science_reviewer_user_id","science_username","science_real_name","science_roles"),
                "scienceReviewOpinion",row.get("science_review_opinion"),"scienceReviewRemark",row.get("science_review_remark"),
                "scienceReviewedAt",time(row,"science_reviewed_at"),"createdAt",time(row,"created_at"),"updatedAt",time(row,"updated_at"))).toList();
    }
    private static Long number(Map<String,Object> row,String key) {
        return row.get(key)==null?null:((Number)row.get(key)).longValue();
    }
    private static Object time(Map<String,Object> row,String key) {
        Object value=row.get(key);
        return value instanceof java.sql.Timestamp stamp?stamp.toLocalDateTime():value;
    }
    private static Map<String,Object> nullableSummary(Map<String,Object> row,String idKey,String usernameKey,
            String realNameKey,String rolesKey) {
        Long id=number(row,idKey);
        return id==null?null:userSummary(id,(String)row.get(usernameKey),(String)row.get(realNameKey),null,null,(String)row.get(rolesKey));
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

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
