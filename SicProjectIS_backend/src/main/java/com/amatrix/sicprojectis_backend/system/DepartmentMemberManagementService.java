package com.amatrix.sicprojectis_backend.system;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.DepartmentDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberCandidateResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberQueryResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentOptionResponse;
import com.amatrix.sicprojectis_backend.system.dto.SaveDepartmentRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpdateDepartmentMemberRolesRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpsertDepartmentMemberRequest;
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
public class DepartmentMemberManagementService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String PROJECT_LEADER = "PROJECT_LEADER";
    private static final String EXPERT = "EXPERT";
    private static final String FINANCE_ADMIN = "FINANCE_ADMIN";
    private static final String NO_DEPARTMENT_MESSAGE = "请联系系统管理员分配部门";

    private final AppUserDao appUserDao;
    private final DepartmentDao departmentDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionService permissionService;
    private final AdminAuditLogService adminAuditLogService;

    public DepartmentMemberManagementService(
            AppUserDao appUserDao,
            DepartmentDao departmentDao,
            RoleDao roleDao,
            UserRoleDao userRoleDao,
            UserRoleDetailViewDao userRoleDetailViewDao,
            PermissionService permissionService,
            AdminAuditLogService adminAuditLogService) {
        this.appUserDao = appUserDao;
        this.departmentDao = departmentDao;
        this.roleDao = roleDao;
        this.userRoleDao = userRoleDao;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionService = permissionService;
        this.adminAuditLogService = adminAuditLogService;
    }

    public DepartmentMemberQueryResponse members(AuthenticatedUser currentUser, Long deptId) {
        requireManager(currentUser.userId());
        Lookup lookup = buildLookup();
        boolean disabled = memberManagementDisabled(currentUser.userId());
        Long effectiveDeptId = disabled ? null : effectiveDeptId(currentUser.userId(), deptId);
        List<DepartmentMemberResponse> members = disabled
                ? List.of()
                : appUserDao.selectAll().stream()
                        .filter(user -> effectiveDeptId == null || Objects.equals(user.getDeptId(), effectiveDeptId))
                        .sorted(Comparator.comparing(AppUser::getUserId))
                        .map(user -> toMemberResponse(user, lookup, assignableRoleCodes(currentUser.userId())))
                        .toList();
        return new DepartmentMemberQueryResponse(
                visibleDepartments(currentUser.userId(), lookup),
                assignableRoleCodes(currentUser.userId()),
                members,
                canManageDepartments(currentUser.userId()),
                disabled,
                disabled ? NO_DEPARTMENT_MESSAGE : null);
    }

    public DepartmentMemberCandidateResponse candidates(AuthenticatedUser currentUser, String keyword) {
        requireManager(currentUser.userId());
        ensureMemberManagementEnabled(currentUser.userId());
        Lookup lookup = buildLookup();
        String normalized = normalizeSearch(keyword);
        List<String> assignable = assignableRoleCodes(currentUser.userId());
        List<DepartmentMemberResponse> users = appUserDao.selectAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> normalized == null || searchableText(user, lookup).contains(normalized))
                .sorted(Comparator.comparing(AppUser::getUserId))
                .limit(50)
                .map(user -> toMemberResponse(user, lookup, assignable))
                .toList();
        return new DepartmentMemberCandidateResponse(users);
    }

    public List<DepartmentOptionResponse> departments(AuthenticatedUser currentUser) {
        requireManager(currentUser.userId());
        return visibleDepartments(currentUser.userId(), buildLookup());
    }

    @Transactional
    public DepartmentOptionResponse createDepartment(AuthenticatedUser currentUser, SaveDepartmentRequest request) {
        requireSystemAdmin(currentUser.userId());
        Department department = new Department();
        department.setDeptCode(normalizeOptional(request.deptCode()));
        department.setDeptName(normalizeRequired(request.deptName(), "Department name is required"));
        department.setParentDeptId(request.parentDeptId());
        department.setEnabled(request.enabled() == null ? true : request.enabled());
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        departmentDao.insert(department);
        logDepartmentChange(currentUser.userId(), "CREATE_DEPARTMENT", null, department.toString(), "Department created");
        return toDepartmentOption(department);
    }

    @Transactional
    public DepartmentOptionResponse updateDepartment(AuthenticatedUser currentUser, Long deptId, SaveDepartmentRequest request) {
        requireSystemAdmin(currentUser.userId());
        Department department = requireDepartment(deptId);
        String before = department.toString();
        department.setDeptCode(normalizeOptional(request.deptCode()));
        department.setDeptName(normalizeRequired(request.deptName(), "Department name is required"));
        department.setParentDeptId(request.parentDeptId());
        department.setEnabled(request.enabled() == null ? true : request.enabled());
        department.setUpdatedAt(LocalDateTime.now());
        departmentDao.updateById(department);
        logDepartmentChange(currentUser.userId(), "UPDATE_DEPARTMENT", before, department.toString(), "Department updated");
        return toDepartmentOption(department);
    }

    @Transactional
    public void deleteDepartment(AuthenticatedUser currentUser, Long deptId) {
        requireSystemAdmin(currentUser.userId());
        Department department = requireDepartment(deptId);
        for (AppUser user : appUserDao.selectAll()) {
            if (Objects.equals(user.getDeptId(), deptId)) {
                user.setDeptId(null);
                user.setUpdatedAt(LocalDateTime.now());
                appUserDao.updateById(user);
            }
        }
        departmentDao.deleteById(deptId);
        logDepartmentChange(currentUser.userId(), "DELETE_DEPARTMENT", department.toString(), null, "Department deleted");
    }

    @Transactional
    public DepartmentMemberResponse upsertMember(
            AuthenticatedUser currentUser,
            Long deptId,
            Long userId,
            UpsertDepartmentMemberRequest request) {
        requireManager(currentUser.userId());
        ensureMemberManagementEnabled(currentUser.userId());
        Department department = requireDepartment(deptId);
        if (!Boolean.TRUE.equals(department.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is disabled");
        }
        ensureCanManageDepartment(currentUser.userId(), deptId);
        AppUser user = requireUser(userId);
        Long beforeDeptId = user.getDeptId();
        user.setDeptId(deptId);
        user.setUpdatedAt(LocalDateTime.now());
        appUserDao.updateById(user);

        AppUser updated = requireUser(userId);
        logDepartmentMemberChange(
                currentUser.userId(),
                userId,
                "UPSERT",
                "deptId=" + beforeDeptId,
                "deptId=" + updated.getDeptId(),
                "Department member assigned");
        return toMemberResponse(updated, buildLookup(), assignableRoleCodes(currentUser.userId()));
    }

    @Transactional
    public DepartmentMemberResponse updateRoles(
            AuthenticatedUser currentUser,
            Long userId,
            UpdateDepartmentMemberRolesRequest request) {
        requireManager(currentUser.userId());
        ensureMemberManagementEnabled(currentUser.userId());
        AppUser user = requireUser(userId);
        ensureCanManageDepartment(currentUser.userId(), user.getDeptId());
        List<String> beforeRoles = roleCodes(userId);
        updateAssignableRoles(currentUser.userId(), userId, request.roleCodes());
        List<String> afterRoles = roleCodes(userId);
        logDepartmentMemberChange(
                currentUser.userId(),
                userId,
                "UPDATE_ROLES",
                beforeRoles.toString(),
                afterRoles.toString(),
                "Department member roles updated");
        return toMemberResponse(requireUser(userId), buildLookup(), assignableRoleCodes(currentUser.userId()));
    }

    @Transactional
    public DepartmentMemberResponse removeMember(AuthenticatedUser currentUser, Long userId) {
        requireManager(currentUser.userId());
        ensureMemberManagementEnabled(currentUser.userId());
        AppUser user = requireUser(userId);
        ensureCanManageDepartment(currentUser.userId(), user.getDeptId());
        Long beforeDeptId = user.getDeptId();
        user.setDeptId(null);
        user.setUpdatedAt(LocalDateTime.now());
        appUserDao.updateById(user);
        logDepartmentMemberChange(
                currentUser.userId(),
                userId,
                "REMOVE_MEMBER",
                "deptId=" + beforeDeptId,
                "deptId=null",
                "Department member removed");
        return toMemberResponse(requireUser(userId), buildLookup(), assignableRoleCodes(currentUser.userId()));
    }

    private void updateAssignableRoles(Long operatorUserId, Long userId, List<String> requestedRoleCodes) {
        Set<String> assignable = new LinkedHashSet<>(assignableRoleCodes(operatorUserId));
        Set<String> requested = requestedRoleCodes == null
                ? Set.of()
                : requestedRoleCodes.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(roleCode -> !roleCode.isEmpty())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String roleCode : requested) {
            if (!assignable.contains(roleCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not assignable here: " + roleCode);
            }
        }
        Map<String, Role> assignableRolesByCode = roleDao.selectAll().stream()
                .filter(role -> assignable.contains(role.getRoleCode()))
                .filter(role -> Boolean.TRUE.equals(role.getEnabled()))
                .collect(java.util.stream.Collectors.toMap(Role::getRoleCode, role -> role, (left, right) -> left, LinkedHashMap::new));
        for (String roleCode : requested) {
            if (!assignableRolesByCode.containsKey(roleCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is disabled or missing: " + roleCode);
            }
        }
        for (Role role : assignableRolesByCode.values()) {
            UserRole existing = userRoleDao.selectByUserIdAndRoleId(userId, role.getRoleId());
            if (requested.contains(role.getRoleCode())) {
                if (existing == null) {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(role.getRoleId());
                    userRole.setAssignedAt(LocalDateTime.now());
                    userRoleDao.insert(userRole);
                }
            } else if (existing != null) {
                userRoleDao.deleteByUserIdAndRoleId(userId, role.getRoleId());
            }
        }
    }

    private List<String> assignableRoleCodes(Long operatorUserId) {
        if (permissionService.hasRole(operatorUserId, SYSTEM_ADMIN) || permissionService.hasRole(operatorUserId, SCIENCE_ADMIN)) {
            return List.of(PROJECT_LEADER, EXPERT, FINANCE_ADMIN);
        }
        if (permissionService.hasRole(operatorUserId, DEPT_ADMIN)) {
            return List.of(PROJECT_LEADER);
        }
        return List.of();
    }

    private Long effectiveDeptId(Long operatorUserId, Long requestedDeptId) {
        if (permissionService.hasRole(operatorUserId, SYSTEM_ADMIN) || permissionService.hasRole(operatorUserId, SCIENCE_ADMIN)) {
            return requestedDeptId;
        }
        Long ownDeptId = requireUser(operatorUserId).getDeptId();
        if (requestedDeptId != null && !Objects.equals(requestedDeptId, ownDeptId)) {
            throw forbidden("You can only manage members in your department");
        }
        return ownDeptId;
    }

    private void ensureCanManageDepartment(Long operatorUserId, Long deptId) {
        if (permissionService.hasRole(operatorUserId, SYSTEM_ADMIN) || permissionService.hasRole(operatorUserId, SCIENCE_ADMIN)) {
            return;
        }
        if (permissionService.hasRole(operatorUserId, DEPT_ADMIN)
                && requireUser(operatorUserId).getDeptId() != null
                && Objects.equals(requireUser(operatorUserId).getDeptId(), deptId)) {
            return;
        }
        throw forbidden("You can only manage members in your department");
    }

    private void requireManager(Long userId) {
        if (!permissionService.hasRole(userId, SYSTEM_ADMIN)
                && !permissionService.hasRole(userId, SCIENCE_ADMIN)
                && !permissionService.hasRole(userId, DEPT_ADMIN)) {
            throw forbidden("You do not have permission to manage departments");
        }
    }

    private void requireSystemAdmin(Long userId) {
        if (!permissionService.hasRole(userId, SYSTEM_ADMIN)) {
            throw forbidden("Only system administrators can manage department definitions");
        }
    }

    private boolean canManageDepartments(Long userId) {
        return permissionService.hasRole(userId, SYSTEM_ADMIN);
    }

    private boolean memberManagementDisabled(Long userId) {
        return permissionService.hasRole(userId, DEPT_ADMIN)
                && !permissionService.hasRole(userId, SYSTEM_ADMIN)
                && !permissionService.hasRole(userId, SCIENCE_ADMIN)
                && requireUser(userId).getDeptId() == null;
    }

    private void ensureMemberManagementEnabled(Long userId) {
        if (memberManagementDisabled(userId)) {
            throw forbidden(NO_DEPARTMENT_MESSAGE);
        }
    }

    private List<DepartmentOptionResponse> visibleDepartments(Long operatorUserId, Lookup lookup) {
        if (permissionService.hasRole(operatorUserId, SYSTEM_ADMIN) || permissionService.hasRole(operatorUserId, SCIENCE_ADMIN)) {
            return lookup.departmentById.values().stream()
                    .sorted(Comparator.comparing(Department::getDeptId))
                    .map(this::toDepartmentOption)
                    .toList();
        }
        Long ownDeptId = requireUser(operatorUserId).getDeptId();
        Department department = ownDeptId == null ? null : lookup.departmentById.get(ownDeptId);
        return department == null ? List.of() : List.of(toDepartmentOption(department));
    }

    private DepartmentMemberResponse toMemberResponse(AppUser user, Lookup lookup, List<String> editableRoleCodes) {
        Department department = user.getDeptId() == null ? null : lookup.departmentById.get(user.getDeptId());
        return new DepartmentMemberResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                department == null ? null : department.getDeptName(),
                user.getPhone(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled()),
                roleCodes(user.getUserId()),
                editableRoleCodes);
    }

    private DepartmentOptionResponse toDepartmentOption(Department department) {
        return new DepartmentOptionResponse(
                department.getDeptId(),
                department.getDeptCode(),
                department.getDeptName(),
                department.getParentDeptId(),
                Boolean.TRUE.equals(department.getEnabled()));
    }

    private String searchableText(AppUser user, Lookup lookup) {
        Department department = user.getDeptId() == null ? null : lookup.departmentById.get(user.getDeptId());
        return String.join(" ",
                nullSafe(user.getUsername()),
                nullSafe(user.getRealName()),
                nullSafe(user.getPhone()),
                nullSafe(user.getEmail()),
                department == null ? "" : nullSafe(department.getDeptName()),
                String.join(" ", roleCodes(user.getUserId()))).toLowerCase(java.util.Locale.ROOT);
    }

    private List<String> roleCodes(Long userId) {
        return userRoleDetailViewDao.selectByUserId(userId).stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private Lookup buildLookup() {
        Map<Long, Department> departmentById = departmentDao.selectAll().stream()
                .collect(LinkedHashMap::new, (map, department) -> map.put(department.getDeptId(), department), Map::putAll);
        return new Lookup(departmentById);
    }

    private Department requireDepartment(Long deptId) {
        Department department = deptId == null ? null : departmentDao.selectById(deptId);
        if (department == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found");
        }
        return department;
    }

    private AppUser requireUser(Long userId) {
        AppUser user = userId == null ? null : appUserDao.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is disabled or missing");
        }
        return user;
    }

    private void logDepartmentMemberChange(
            Long operatorUserId,
            Long targetUserId,
            String actionType,
            String before,
            String after,
            String remark) {
        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("DEPARTMENT_MEMBER");
        log.setActionType(actionType);
        log.setOperatorUserId(operatorUserId);
        log.setTargetUserId(targetUserId);
        log.setBeforeSnapshotJson(before);
        log.setAfterSnapshotJson(after);
        log.setRemark(remark);
        adminAuditLogService.logOperation(log);
    }

    private void logDepartmentChange(Long operatorUserId, String actionType, String before, String after, String remark) {
        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("DEPARTMENT");
        log.setActionType(actionType);
        log.setOperatorUserId(operatorUserId);
        log.setBeforeSnapshotJson(before);
        log.setAfterSnapshotJson(after);
        log.setRemark(remark);
        adminAuditLogService.logOperation(log);
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

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private record Lookup(Map<Long, Department> departmentById) {
    }
}