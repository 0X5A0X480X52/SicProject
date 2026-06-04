package com.amatrix.sicprojectis_backend.system;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.amatrix.sicprojectis_backend.system.dto.AdminRoleOptionResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserDetailResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserListItemResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserQueryResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserRoleRecordResponse;
import com.amatrix.sicprojectis_backend.system.dto.ChangeDiffSummaryResponse;
import com.amatrix.sicprojectis_backend.system.dto.UpdateUserRolesRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpdateUserStatusRequest;
import com.amatrix.sicprojectis_backend.system.dto.UserRoleManagementResponse;
import com.amatrix.sicprojectis_backend.system.dto.UserRoleUpdateResponse;
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
public class SystemRoleManagementService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final AppUserDao appUserDao;
    private final DepartmentDao departmentDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionService permissionService;
    private final AdminAuditLogService adminAuditLogService;

    public SystemRoleManagementService(
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

    public UserRoleManagementResponse getRoleManagement(AuthenticatedUser currentUser) {
        requireSystemAdmin(currentUser.userId());
        AdminUserQueryResponse query = queryUsers(currentUser, null, null, null, null, null);
        return new UserRoleManagementResponse(
                query.roles(),
                query.users().stream()
                        .map(user -> new AdminUserRoleRecordResponse(
                                user.userId(),
                                user.username(),
                                user.realName(),
                                user.deptId(),
                                user.deptName(),
                                user.enabled(),
                                user.roleCodes()))
                        .toList());
    }

    public AdminUserQueryResponse queryUsers(
            AuthenticatedUser currentUser,
            String username,
            String realName,
            Long deptId,
            Boolean enabled,
            String roleCode) {
        requireSystemAdmin(currentUser.userId());
        Lookup lookup = buildLookup();
        String normalizedUsername = normalizeSearch(username);
        String normalizedRealName = normalizeSearch(realName);
        String normalizedRoleCode = normalizeSearch(roleCode);

        List<AdminUserListItemResponse> users = appUserDao.selectAll().stream()
                .sorted(Comparator.comparing(AppUser::getUserId))
                .map(user -> toUserListItem(currentUser.userId(), user, lookup))
                .filter(user -> normalizedUsername == null || user.username().toLowerCase(Locale.ROOT).contains(normalizedUsername))
                .filter(user -> normalizedRealName == null || user.realName().toLowerCase(Locale.ROOT).contains(normalizedRealName))
                .filter(user -> deptId == null || Objects.equals(user.deptId(), deptId))
                .filter(user -> enabled == null || user.enabled() == enabled)
                .filter(user -> normalizedRoleCode == null || user.roleCodes().stream()
                        .map(code -> code.toLowerCase(Locale.ROOT))
                        .anyMatch(code -> code.contains(normalizedRoleCode)))
                .toList();

        return new AdminUserQueryResponse(lookup.roles, users);
    }

    public AdminUserDetailResponse getUserDetail(AuthenticatedUser currentUser, Long userId) {
        requireSystemAdmin(currentUser.userId());
        AppUser user = requireUser(userId);
        Lookup lookup = buildLookup();
        return toUserDetail(currentUser.userId(), user, lookup);
    }

    @Transactional
    public UserRoleUpdateResponse updateUserRoles(
            AuthenticatedUser currentUser,
            Long userId,
            UpdateUserRolesRequest request) {
        requireSystemAdmin(currentUser.userId());
        AppUser user = requireUser(userId);

        Set<String> requestedRoleCodes = request.roleCodes() == null
                ? Set.of()
                : request.roleCodes().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(roleCode -> !roleCode.isEmpty())
                        .collect(java.util.stream.Collectors.toSet());

        Map<String, Role> roleByCode = roleDao.selectAll().stream()
                .filter(role -> Boolean.TRUE.equals(role.getEnabled()))
                .collect(java.util.stream.Collectors.toMap(Role::getRoleCode, role -> role, (left, right) -> left, LinkedHashMap::new));

        for (String roleCode : requestedRoleCodes) {
            if (!roleByCode.containsKey(roleCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code: " + roleCode);
            }
        }

        List<UserRole> existingUserRoles = userRoleDao.selectByUserId(userId);
        Set<String> existingRoleCodes = existingUserRoles.stream()
                .map(UserRole::getRoleId)
                .map(roleId -> roleDao.selectById(roleId))
                .filter(Objects::nonNull)
                .map(Role::getRoleCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedRoleIds = requestedRoleCodes.stream()
                .map(roleByCode::get)
                .map(Role::getRoleId)
                .collect(java.util.stream.Collectors.toSet());

        for (Role role : roleByCode.values()) {
            UserRole existing = userRoleDao.selectByUserIdAndRoleId(userId, role.getRoleId());
            if (requestedRoleIds.contains(role.getRoleId())) {
                if (existing == null) {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(role.getRoleId());
                    userRole.setAssignedAt(LocalDateTime.now());
                    userRoleDao.insert(userRole);
                }
            } else if (existing != null) {
                if (SYSTEM_ADMIN.equals(role.getRoleCode()) && Objects.equals(currentUser.userId(), userId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own SYSTEM_ADMIN role");
                }
                userRoleDao.deleteByUserIdAndRoleId(userId, role.getRoleId());
            }
        }

        ChangeDiffSummaryResponse diff = diff(existingRoleCodes, requestedRoleCodes);
        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("SYSTEM_ROLE");
        log.setActionType("UPDATE");
        log.setOperatorUserId(currentUser.userId());
        log.setTargetUserId(userId);
        log.setBeforeSnapshotJson(existingRoleCodes.stream().sorted().toList().toString());
        log.setAfterSnapshotJson(requestedRoleCodes.stream().sorted().toList().toString());
        log.setRemark("added=" + diff.added() + ", removed=" + diff.removed());
        adminAuditLogService.logOperation(log);

        Lookup lookup = buildLookup();
        return new UserRoleUpdateResponse(
                toUserDetail(currentUser.userId(), user, lookup),
                diff,
                queryUsers(currentUser, null, null, null, null, null));
    }

    @Transactional
    public AdminUserDetailResponse updateUserStatus(
            AuthenticatedUser currentUser,
            Long userId,
            UpdateUserStatusRequest request) {
        requireSystemAdmin(currentUser.userId());
        if (Objects.equals(currentUser.userId(), userId) && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own account");
        }
        AppUser user = requireUser(userId);
        boolean before = Boolean.TRUE.equals(user.getEnabled());
        user.setEnabled(request.enabled());
        user.setUpdatedAt(LocalDateTime.now());
        appUserDao.updateById(user);

        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("SYSTEM_ROLE");
        log.setActionType("UPDATE");
        log.setOperatorUserId(currentUser.userId());
        log.setTargetUserId(userId);
        log.setBeforeSnapshotJson("{enabled=" + before + "}");
        log.setAfterSnapshotJson("{enabled=" + request.enabled() + "}");
        log.setRemark("User status updated");
        adminAuditLogService.logOperation(log);

        return toUserDetail(currentUser.userId(), requireUser(userId), buildLookup());
    }

    private Lookup buildLookup() {
        Map<Long, Department> departmentById = departmentDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Department::getDeptId,
                        department -> department,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, List<UserRoleDetailView>> detailsByUserId = userRoleDetailViewDao.selectAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        UserRoleDetailView::getUserId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        List<AdminRoleOptionResponse> roles = roleDao.selectAll().stream()
                .sorted(Comparator.comparing(Role::getRoleId))
                .map(role -> new AdminRoleOptionResponse(
                        role.getRoleId(),
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getRoleDesc(),
                        Boolean.TRUE.equals(role.getEnabled())))
                .toList();

        return new Lookup(departmentById, detailsByUserId, roles);
    }

    private AdminUserListItemResponse toUserListItem(Long operatorUserId, AppUser user, Lookup lookup) {
        List<String> roleCodes = lookup.detailsByUserId.getOrDefault(user.getUserId(), List.of()).stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Department department = user.getDeptId() == null ? null : lookup.departmentById.get(user.getDeptId());
        return new AdminUserListItemResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                department == null ? null : department.getDeptName(),
                Boolean.TRUE.equals(user.getEnabled()),
                roleCodes,
                permissionService.hasRole(operatorUserId, SYSTEM_ADMIN),
                permissionService.hasRole(operatorUserId, SYSTEM_ADMIN) && !Objects.equals(operatorUserId, user.getUserId()));
    }

    private AdminUserDetailResponse toUserDetail(Long operatorUserId, AppUser user, Lookup lookup) {
        AdminUserListItemResponse base = toUserListItem(operatorUserId, user, lookup);
        return new AdminUserDetailResponse(
                base.userId(),
                base.username(),
                base.realName(),
                base.deptId(),
                base.deptName(),
                user.getPhone(),
                user.getEmail(),
                base.enabled(),
                base.roleCodes(),
                base.canEditRoles(),
                base.canToggleStatus());
    }

    private ChangeDiffSummaryResponse diff(Set<String> before, Set<String> after) {
        List<String> added = after.stream()
                .filter(roleCode -> !before.contains(roleCode))
                .sorted()
                .toList();
        List<String> removed = before.stream()
                .filter(roleCode -> !after.contains(roleCode))
                .sorted()
                .toList();
        return new ChangeDiffSummaryResponse(added, removed);
    }

    private AppUser requireUser(Long userId) {
        AppUser user = appUserDao.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void requireSystemAdmin(Long userId) {
        if (!permissionService.hasRole(userId, SYSTEM_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage system roles");
        }
    }

    private record Lookup(
            Map<Long, Department> departmentById,
            Map<Long, List<UserRoleDetailView>> detailsByUserId,
            List<AdminRoleOptionResponse> roles) {
    }
}
