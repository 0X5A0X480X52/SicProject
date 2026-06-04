package com.amatrix.sicprojectis_backend.system;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.RolePermissionDao;
import com.amatrix.sicprojectis_backend.system.dto.AdminRoleOptionResponse;
import com.amatrix.sicprojectis_backend.system.dto.ChangeDiffSummaryResponse;
import com.amatrix.sicprojectis_backend.system.dto.PermissionDefinitionResponse;
import com.amatrix.sicprojectis_backend.system.dto.RolePermissionMatrixResponse;
import com.amatrix.sicprojectis_backend.system.dto.RolePermissionMatrixRowResponse;
import com.amatrix.sicprojectis_backend.system.dto.RolePermissionUpdateResponse;
import com.amatrix.sicprojectis_backend.system.dto.UpdateRolePermissionsRequest;
import com.amatrix.sicprojectis_backend.system.entity.AdminOperationLog;
import com.amatrix.sicprojectis_backend.system.entity.Permission;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.system.entity.RolePermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RolePermissionManagementService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final RoleDao roleDao;
    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final PermissionService permissionService;
    private final AdminAuditLogService adminAuditLogService;

    public RolePermissionManagementService(
            RoleDao roleDao,
            PermissionDao permissionDao,
            RolePermissionDao rolePermissionDao,
            PermissionService permissionService,
            AdminAuditLogService adminAuditLogService) {
        this.roleDao = roleDao;
        this.permissionDao = permissionDao;
        this.rolePermissionDao = rolePermissionDao;
        this.permissionService = permissionService;
        this.adminAuditLogService = adminAuditLogService;
    }

    public RolePermissionMatrixResponse getMatrix(AuthenticatedUser currentUser) {
        requireSystemAdmin(currentUser.userId());
        return buildMatrix();
    }

    public List<PermissionDefinitionResponse> getPermissions(AuthenticatedUser currentUser) {
        requireSystemAdmin(currentUser.userId());
        return buildPermissionDefinitions();
    }

    @Transactional
    public RolePermissionUpdateResponse updateRolePermissions(
            AuthenticatedUser currentUser,
            String roleCode,
            UpdateRolePermissionsRequest request) {
        requireSystemAdmin(currentUser.userId());
        Role role = roleDao.selectByCode(roleCode);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }

        Set<String> requestedCodes = request.permissionCodes() == null
                ? Set.of()
                : request.permissionCodes().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(code -> !code.isEmpty())
                        .collect(java.util.stream.Collectors.toSet());
        Map<String, Permission> permissionByCode = permissionDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Permission::getPermissionCode,
                        permission -> permission,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (String code : requestedCodes) {
            if (!permissionByCode.containsKey(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission code: " + code);
            }
        }

        List<RolePermission> existingMappings = rolePermissionDao.selectByRoleId(role.getRoleId());
        Set<String> existingCodes = existingMappings.stream()
                .map(RolePermission::getPermissionId)
                .map(permissionDao::selectById)
                .filter(Objects::nonNull)
                .map(Permission::getPermissionCode)
                .collect(java.util.stream.Collectors.toSet());

        for (Permission permission : permissionByCode.values()) {
            RolePermission existing = rolePermissionDao.selectByRoleIdAndPermissionId(role.getRoleId(), permission.getPermissionId());
            if (requestedCodes.contains(permission.getPermissionCode())) {
                if (existing == null) {
                    RolePermission mapping = new RolePermission();
                    mapping.setRoleId(role.getRoleId());
                    mapping.setPermissionId(permission.getPermissionId());
                    rolePermissionDao.insert(mapping);
                }
            } else if (existing != null) {
                rolePermissionDao.deleteByRoleIdAndPermissionId(role.getRoleId(), permission.getPermissionId());
            }
        }

        ChangeDiffSummaryResponse diff = diff(existingCodes, requestedCodes);
        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("ROLE_PERMISSION");
        log.setActionType("UPDATE");
        log.setOperatorUserId(currentUser.userId());
        log.setRoleCode(roleCode);
        log.setBeforeSnapshotJson(existingCodes.stream().sorted().toList().toString());
        log.setAfterSnapshotJson(requestedCodes.stream().sorted().toList().toString());
        log.setRemark("added=" + diff.added() + ", removed=" + diff.removed());
        adminAuditLogService.logOperation(log);

        return new RolePermissionUpdateResponse(roleCode, diff, buildMatrix());
    }

    private RolePermissionMatrixResponse buildMatrix() {
        List<AdminRoleOptionResponse> roles = roleDao.selectAll().stream()
                .sorted(Comparator.comparing(Role::getRoleId))
                .map(role -> new AdminRoleOptionResponse(
                        role.getRoleId(),
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getRoleDesc(),
                        Boolean.TRUE.equals(role.getEnabled())))
                .toList();
        List<PermissionDefinitionResponse> permissions = buildPermissionDefinitions();
        Map<Long, String> permissionCodeById = permissionDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Permission::getPermissionId,
                        Permission::getPermissionCode,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<RolePermissionMatrixRowResponse> matrix = roles.stream()
                .map(role -> new RolePermissionMatrixRowResponse(
                        role.roleCode(),
                        role.roleName(),
                        role.enabled(),
                        rolePermissionDao.selectByRoleId(role.roleId()).stream()
                                .map(RolePermission::getPermissionId)
                                .map(permissionCodeById::get)
                                .filter(Objects::nonNull)
                                .sorted()
                                .toList()))
                .toList();
        return new RolePermissionMatrixResponse(roles, permissions, matrix);
    }

    private List<PermissionDefinitionResponse> buildPermissionDefinitions() {
        return permissionDao.selectAll().stream()
                .sorted(Comparator.comparing(Permission::getPermissionId))
                .map(permission -> new PermissionDefinitionResponse(
                        permission.getPermissionId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName(),
                        inferPermissionGroup(permission.getPermissionCode()),
                        permission.getPermissionDesc()))
                .toList();
    }

    private String inferPermissionGroup(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return "misc";
        }
        int index = permissionCode.indexOf(':');
        return (index < 0 ? permissionCode : permissionCode.substring(0, index)).toLowerCase(Locale.ROOT);
    }

    private ChangeDiffSummaryResponse diff(Set<String> before, Set<String> after) {
        List<String> added = after.stream().filter(code -> !before.contains(code)).sorted().toList();
        List<String> removed = before.stream().filter(code -> !after.contains(code)).sorted().toList();
        return new ChangeDiffSummaryResponse(added, removed);
    }

    private void requireSystemAdmin(Long userId) {
        if (!permissionService.hasRole(userId, SYSTEM_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage role permissions");
        }
    }
}
