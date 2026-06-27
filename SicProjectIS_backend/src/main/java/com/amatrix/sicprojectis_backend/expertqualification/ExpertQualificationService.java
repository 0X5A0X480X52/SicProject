package com.amatrix.sicprojectis_backend.expertqualification;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.amatrix.sicprojectis_backend.expertqualification.dao.ExpertQualificationApplicationDao;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ExpertQualificationApplicationQueryResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ExpertQualificationApplicationResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.MyExpertQualificationResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ReviewExpertQualificationRequest;
import com.amatrix.sicprojectis_backend.expertqualification.dto.SubmitExpertQualificationRequest;
import com.amatrix.sicprojectis_backend.expertqualification.entity.ExpertQualificationApplication;
import com.amatrix.sicprojectis_backend.project.dto.UserSummaryResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.AdminAuditLogService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.DepartmentDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.AdminOperationLog;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.system.entity.Department;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.system.entity.UserRole;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpertQualificationService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String EXPERT = "EXPERT";
    private static final String PERMISSION_DEPT_REVIEW = "expert:qualification:review:dept";
    private static final String PERMISSION_SCIENCE_REVIEW = "expert:qualification:review:science";
    private static final String PENDING_DEPT_REVIEW = "PENDING_DEPT_REVIEW";
    private static final String DEPT_REJECTED = "DEPT_REJECTED";
    private static final String PENDING_SCIENCE_REVIEW = "PENDING_SCIENCE_REVIEW";
    private static final String SCIENCE_REJECTED = "SCIENCE_REJECTED";
    private static final String APPROVED = "APPROVED";

    private final ExpertQualificationApplicationDao applicationDao;
    private final AppUserDao appUserDao;
    private final DepartmentDao departmentDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionService permissionService;
    private final AdminAuditLogService adminAuditLogService;

    public ExpertQualificationService(
            ExpertQualificationApplicationDao applicationDao,
            AppUserDao appUserDao,
            DepartmentDao departmentDao,
            RoleDao roleDao,
            UserRoleDao userRoleDao,
            UserRoleDetailViewDao userRoleDetailViewDao,
            PermissionService permissionService,
            AdminAuditLogService adminAuditLogService) {
        this.applicationDao = applicationDao;
        this.appUserDao = appUserDao;
        this.departmentDao = departmentDao;
        this.roleDao = roleDao;
        this.userRoleDao = userRoleDao;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionService = permissionService;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional
    public ExpertQualificationApplicationResponse submit(
            AuthenticatedUser currentUser,
            SubmitExpertQualificationRequest request) {
        AppUser applicant = requireEnabledUser(currentUser.userId());
        if (permissionService.hasRole(applicant.getUserId(), EXPERT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user already has EXPERT role");
        }
        if (applicationDao.countActiveByApplicantUserId(applicant.getUserId()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is already an active expert qualification application");
        }
        String specialty = normalizeRequired(request.specialty(), "Specialty is required");
        String reason = normalizeRequired(request.applicationReason(), "Application reason is required");
        LocalDateTime now = LocalDateTime.now();

        ExpertQualificationApplication application = new ExpertQualificationApplication();
        application.setApplicantUserId(applicant.getUserId());
        application.setApplicantDeptId(applicant.getDeptId());
        application.setSpecialty(specialty);
        application.setProfessionalTitle(normalizeOptional(request.professionalTitle()));
        application.setApplicationReason(reason);
        application.setStatus(PENDING_DEPT_REVIEW);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationDao.insert(application);

        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("EXPERT_QUALIFICATION");
        log.setActionType("SUBMIT");
        log.setOperatorUserId(currentUser.userId());
        log.setTargetUserId(currentUser.userId());
        log.setAfterSnapshotJson(application.toString());
        log.setRemark("Expert qualification application submitted");
        adminAuditLogService.logOperation(log);

        return toResponse(application, buildLookup());
    }

    public MyExpertQualificationResponse myApplications(AuthenticatedUser currentUser) {
        List<ExpertQualificationApplicationResponse> applications = applicationDao.selectByApplicantUserId(currentUser.userId()).stream()
                .map(application -> toResponse(application, buildLookup()))
                .toList();
        return new MyExpertQualificationResponse(
                permissionService.hasRole(currentUser.userId(), EXPERT),
                applicationDao.countActiveByApplicantUserId(currentUser.userId()) > 0,
                applications);
    }

    public ExpertQualificationApplicationQueryResponse queryAdminApplications(AuthenticatedUser currentUser) {
        boolean systemAdmin = permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN);
        boolean deptReviewer = canDeptReview(currentUser.userId());
        boolean scienceReviewer = canScienceReview(currentUser.userId());
        if (!systemAdmin && !deptReviewer && !scienceReviewer) {
            throw forbidden("You do not have permission to review expert qualification applications");
        }
        Long currentDeptId = currentUserDeptId(currentUser.userId());
        Lookup lookup = buildLookup();
        List<ExpertQualificationApplicationResponse> applications = applicationDao.selectAll().stream()
                .filter(application -> {
                    if (systemAdmin || scienceReviewer) {
                        return true;
                    }
                    return application.getApplicantDeptId() == null
                            || (currentDeptId != null && Objects.equals(application.getApplicantDeptId(), currentDeptId));
                })
                .sorted(Comparator.comparing(ExpertQualificationApplication::getApplicationId).reversed())
                .map(application -> toResponse(application, lookup))
                .toList();
        return new ExpertQualificationApplicationQueryResponse(applications);
    }

    @Transactional
    public ExpertQualificationApplicationResponse reviewByDept(
            AuthenticatedUser currentUser,
            Long applicationId,
            ReviewExpertQualificationRequest request) {
        boolean systemAdmin = permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN);
        if (!systemAdmin && !canDeptReview(currentUser.userId())) {
            throw forbidden("You do not have permission to perform department review");
        }
        ExpertQualificationApplication application = requireApplication(applicationId);
        if (!PENDING_DEPT_REVIEW.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not pending department review");
        }
        Long currentDeptId = currentUserDeptId(currentUser.userId());
        if (!systemAdmin && (currentDeptId == null || !Objects.equals(currentDeptId, application.getApplicantDeptId()))) {
            throw forbidden("You can only review applications from your department");
        }
        ExpertQualificationApplication before = copy(application);
        LocalDateTime now = LocalDateTime.now();
        String opinion = normalizeRequired(request.opinion(), "Review opinion is required");
        application.setDeptReviewerUserId(currentUser.userId());
        application.setDeptReviewOpinion(opinion);
        application.setDeptReviewRemark(normalizeOptional(request.remark()));
        application.setDeptReviewedAt(now);
        application.setStatus(request.approved() ? PENDING_SCIENCE_REVIEW : DEPT_REJECTED);
        application.setUpdatedAt(now);
        applicationDao.updateById(application);
        logReview(currentUser.userId(), application, before, request.approved() ? "DEPT_APPROVE" : "DEPT_REJECT", opinion, request.remark());
        return toResponse(application, buildLookup());
    }

    @Transactional
    public ExpertQualificationApplicationResponse reviewByScience(
            AuthenticatedUser currentUser,
            Long applicationId,
            ReviewExpertQualificationRequest request) {
        if (!permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN) && !canScienceReview(currentUser.userId())) {
            throw forbidden("You do not have permission to perform science office review");
        }
        ExpertQualificationApplication application = requireApplication(applicationId);
        if (!PENDING_SCIENCE_REVIEW.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not pending science office review");
        }
        ExpertQualificationApplication before = copy(application);
        LocalDateTime now = LocalDateTime.now();
        String opinion = normalizeRequired(request.opinion(), "Review opinion is required");
        application.setScienceReviewerUserId(currentUser.userId());
        application.setScienceReviewOpinion(opinion);
        application.setScienceReviewRemark(normalizeOptional(request.remark()));
        application.setScienceReviewedAt(now);
        application.setStatus(request.approved() ? APPROVED : SCIENCE_REJECTED);
        application.setUpdatedAt(now);
        applicationDao.updateById(application);
        if (request.approved()) {
            ensureExpertRole(application.getApplicantUserId(), currentUser.userId());
        }
        logReview(currentUser.userId(), application, before, request.approved() ? "SCIENCE_APPROVE" : "SCIENCE_REJECT", opinion, request.remark());
        return toResponse(application, buildLookup());
    }

    private boolean canDeptReview(Long userId) {
        return permissionService.hasRole(userId, DEPT_ADMIN) || permissionService.hasPermission(userId, PERMISSION_DEPT_REVIEW);
    }

    private boolean canScienceReview(Long userId) {
        return permissionService.hasRole(userId, SCIENCE_ADMIN) || permissionService.hasPermission(userId, PERMISSION_SCIENCE_REVIEW);
    }

    private void ensureExpertRole(Long userId, Long operatorUserId) {
        Role role = roleDao.selectByCode(EXPERT);
        if (role == null || !Boolean.TRUE.equals(role.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPERT role is not available");
        }
        if (userRoleDao.selectByUserIdAndRoleId(userId, role.getRoleId()) != null) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getRoleId());
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleDao.insert(userRole);

        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("EXPERT_QUALIFICATION");
        log.setActionType("GRANT_ROLE");
        log.setOperatorUserId(operatorUserId);
        log.setTargetUserId(userId);
        log.setRoleCode(EXPERT);
        log.setAfterSnapshotJson("roleCode=" + EXPERT);
        log.setRemark("Granted EXPERT role after qualification approval");
        adminAuditLogService.logOperation(log);
    }

    private void logReview(
            Long operatorUserId,
            ExpertQualificationApplication after,
            ExpertQualificationApplication before,
            String actionType,
            String opinion,
            String remark) {
        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("EXPERT_QUALIFICATION");
        log.setActionType(actionType);
        log.setOperatorUserId(operatorUserId);
        log.setTargetUserId(after.getApplicantUserId());
        log.setBeforeSnapshotJson(before.toString());
        log.setAfterSnapshotJson(after.toString());
        String normalizedRemark = normalizeOptional(remark);
        log.setRemark(normalizedRemark == null ? normalizeOptional(opinion) : normalizedRemark);
        adminAuditLogService.logOperation(log);
    }

    private ExpertQualificationApplicationResponse toResponse(ExpertQualificationApplication application, Lookup lookup) {
        Department department = lookup.departmentById.get(application.getApplicantDeptId());
        return new ExpertQualificationApplicationResponse(
                application.getApplicationId(),
                lookup.userById.get(application.getApplicantUserId()),
                application.getApplicantDeptId(),
                department == null ? null : department.getDeptName(),
                application.getSpecialty(),
                application.getProfessionalTitle(),
                application.getApplicationReason(),
                application.getStatus(),
                lookup.userById.get(application.getDeptReviewerUserId()),
                application.getDeptReviewOpinion(),
                application.getDeptReviewRemark(),
                application.getDeptReviewedAt(),
                lookup.userById.get(application.getScienceReviewerUserId()),
                application.getScienceReviewOpinion(),
                application.getScienceReviewRemark(),
                application.getScienceReviewedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private Lookup buildLookup() {
        Map<Long, Department> departmentById = departmentDao.selectAll().stream()
                .collect(LinkedHashMap::new, (map, department) -> map.put(department.getDeptId(), department), Map::putAll);
        Map<Long, List<UserRoleDetailView>> detailsByUserId = userRoleDetailViewDao.selectAll().stream()
                .filter(detail -> detail.getUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        UserRoleDetailView::getUserId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<Long, UserSummaryResponse> userById = new LinkedHashMap<>();
        for (AppUser user : appUserDao.selectAll()) {
            Department department = user.getDeptId() == null ? null : departmentById.get(user.getDeptId());
            List<String> roleCodes = detailsByUserId.getOrDefault(user.getUserId(), List.of()).stream()
                    .map(UserRoleDetailView::getRoleCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            userById.put(user.getUserId(), new UserSummaryResponse(
                    user.getUserId(),
                    user.getUsername(),
                    user.getRealName(),
                    user.getDeptId(),
                    department == null ? null : department.getDeptName(),
                    roleCodes));
        }
        return new Lookup(userById, departmentById);
    }

    private ExpertQualificationApplication requireApplication(Long applicationId) {
        ExpertQualificationApplication application = applicationDao.selectById(applicationId);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert qualification application not found");
        }
        return application;
    }

    private AppUser requireEnabledUser(Long userId) {
        AppUser user = appUserDao.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is disabled or missing");
        }
        return user;
    }

    private Long currentUserDeptId(Long userId) {
        AppUser user = requireEnabledUser(userId);
        return user.getDeptId();
    }

    private void requireRole(Long userId, String roleCode, String message) {
        if (!permissionService.hasRole(userId, roleCode)) {
            throw forbidden(message);
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ExpertQualificationApplication copy(ExpertQualificationApplication source) {
        ExpertQualificationApplication copy = new ExpertQualificationApplication();
        copy.setApplicationId(source.getApplicationId());
        copy.setApplicantUserId(source.getApplicantUserId());
        copy.setApplicantDeptId(source.getApplicantDeptId());
        copy.setSpecialty(source.getSpecialty());
        copy.setProfessionalTitle(source.getProfessionalTitle());
        copy.setApplicationReason(source.getApplicationReason());
        copy.setStatus(source.getStatus());
        copy.setDeptReviewerUserId(source.getDeptReviewerUserId());
        copy.setDeptReviewOpinion(source.getDeptReviewOpinion());
        copy.setDeptReviewRemark(source.getDeptReviewRemark());
        copy.setDeptReviewedAt(source.getDeptReviewedAt());
        copy.setScienceReviewerUserId(source.getScienceReviewerUserId());
        copy.setScienceReviewOpinion(source.getScienceReviewOpinion());
        copy.setScienceReviewRemark(source.getScienceReviewRemark());
        copy.setScienceReviewedAt(source.getScienceReviewedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private record Lookup(
            Map<Long, UserSummaryResponse> userById,
            Map<Long, Department> departmentById) {
    }
}
