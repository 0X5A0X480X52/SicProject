package com.amatrix.sicprojectis_backend.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.RolePermissionDao;
import com.amatrix.sicprojectis_backend.system.entity.Permission;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.system.entity.RolePermission;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class RolePermissionSeedInitializer implements ApplicationRunner {
    private final RoleDao roleDao;
    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;

    public RolePermissionSeedInitializer(
            RoleDao roleDao,
            PermissionDao permissionDao,
            RolePermissionDao rolePermissionDao) {
        this.roleDao = roleDao;
        this.permissionDao = permissionDao;
        this.rolePermissionDao = rolePermissionDao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, RoleSeed> roles = roleSeeds();
        Map<String, PermissionSeed> permissions = permissionSeeds();

        Map<String, Role> roleByCode = new LinkedHashMap<>();
        for (Map.Entry<String, RoleSeed> entry : roles.entrySet()) {
            roleByCode.put(entry.getKey(), ensureRole(entry.getKey(), entry.getValue()));
        }

        Map<String, Permission> permissionByCode = new LinkedHashMap<>();
        for (Map.Entry<String, PermissionSeed> entry : permissions.entrySet()) {
            permissionByCode.put(entry.getKey(), ensurePermission(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, List<String>> entry : rolePermissionSeeds().entrySet()) {
            Role role = roleByCode.get(entry.getKey());
            if (role == null) {
                continue;
            }
            for (String permissionCode : entry.getValue()) {
                Permission permission = permissionByCode.get(permissionCode);
                if (permission == null) {
                    continue;
                }
                ensureRolePermission(role.getRoleId(), permission.getPermissionId());
            }
        }
    }

    private Role ensureRole(String roleCode, RoleSeed seed) {
        Role existing = roleDao.selectByCode(roleCode);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                roleDao.updateById(existing);
            }
            return existing;
        }

        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(seed.roleName());
        role.setRoleDesc(seed.roleDesc());
        role.setEnabled(true);
        roleDao.insert(role);
        return role;
    }

    private Permission ensurePermission(String permissionCode, PermissionSeed seed) {
        Permission existing = permissionDao.selectByCode(permissionCode);
        if (existing != null) {
            return existing;
        }

        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(seed.permissionName());
        permission.setPermissionType(seed.permissionType());
        permission.setPermissionDesc(seed.permissionDesc());
        permissionDao.insert(permission);
        return permission;
    }

    private void ensureRolePermission(Long roleId, Long permissionId) {
        if (rolePermissionDao.selectByRoleIdAndPermissionId(roleId, permissionId) != null) {
            return;
        }
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        rolePermissionDao.insert(rolePermission);
    }

    private Map<String, RoleSeed> roleSeeds() {
        Map<String, RoleSeed> roles = new LinkedHashMap<>();
        roles.put("SYSTEM_ADMIN", new RoleSeed("System Admin", "System-wide administrator"));
        roles.put("PROJECT_LEADER", new RoleSeed("Project Leader", "Project leader role"));
        roles.put("SCIENCE_ADMIN", new RoleSeed("Science Admin", "Science office administrator"));
        roles.put("DEPT_ADMIN", new RoleSeed("Department Admin", "Department administrator"));
        roles.put("EXPERT", new RoleSeed("Expert", "External or internal project reviewer"));
        roles.put("FINANCE_ADMIN", new RoleSeed("Finance Admin", "Project finance handler"));
        return roles;
    }

    private Map<String, PermissionSeed> permissionSeeds() {
        Map<String, PermissionSeed> permissions = new LinkedHashMap<>();
        permissions.put("auth:login", new PermissionSeed("User Login", "API", "Authenticate a user"));
        permissions.put("auth:me", new PermissionSeed("Current User", "API", "Read the current user context"));
        permissions.put("admin:overview", new PermissionSeed("Admin Overview", "API", "View authorization overview"));
        permissions.put("user:manage", new PermissionSeed("Manage Users", "API", "Manage users and global roles"));
        permissions.put("role:manage", new PermissionSeed("Manage Roles", "API", "Manage global role assignments"));
        permissions.put("permission:manage", new PermissionSeed("Manage Permissions", "API", "Manage role permission mappings"));
        permissions.put("audit:read", new PermissionSeed("Read Audit Logs", "API", "Read authorization audit logs"));
        permissions.put("project:view", new PermissionSeed("View Project", "API", "View project information"));
        permissions.put("project:grant:view", new PermissionSeed("View Project Grants", "API", "View project-level grants"));
        permissions.put("project:grant:leader", new PermissionSeed("Manage Project Leader", "API", "Change project leaders"));
        permissions.put("project:grant:member", new PermissionSeed("Manage Project Members", "API", "Manage project members"));
        permissions.put("project:grant:expert", new PermissionSeed("Manage Project Experts", "API", "Manage project expert grants"));
        permissions.put("project:grant:finance", new PermissionSeed("Manage Project Finance", "API", "Manage project finance grants"));
        permissions.put("project:grant:proxy", new PermissionSeed("Manage Project Proxy", "API", "Manage project proxy recorder grants"));
        permissions.put("project:grant:revoke", new PermissionSeed("Revoke Project Grants", "API", "Revoke project-level grants"));
        permissions.put("workflow:definition:view", new PermissionSeed("View Workflow Definitions", "API", "View workflow definitions"));
        permissions.put("workflow:definition:upload", new PermissionSeed("Upload Workflow Definition", "API", "Upload BPMN workflow definitions"));
        permissions.put("workflow:definition:validate", new PermissionSeed("Validate Workflow Definition", "API", "Validate BPMN workflow definitions"));
        permissions.put("workflow:definition:publish", new PermissionSeed("Publish Workflow Definition", "API", "Publish BPMN workflow definitions"));
        return permissions;
    }

    private Map<String, List<String>> rolePermissionSeeds() {
        List<String> allPermissions = List.copyOf(permissionSeeds().keySet());
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("SYSTEM_ADMIN", allPermissions);
        mapping.put("SCIENCE_ADMIN", List.of(
                "admin:overview",
                "audit:read",
                "project:view",
                "project:grant:view",
                "project:grant:leader",
                "project:grant:member",
                "project:grant:expert",
                "project:grant:finance",
                "project:grant:proxy",
                "project:grant:revoke",
                "workflow:definition:view",
                "workflow:definition:upload",
                "workflow:definition:validate",
                "workflow:definition:publish"));
        mapping.put("DEPT_ADMIN", List.of(
                "project:view",
                "project:grant:view",
                "project:grant:member"));
        mapping.put("PROJECT_LEADER", List.of("project:view"));
        mapping.put("EXPERT", List.of());
        mapping.put("FINANCE_ADMIN", List.of("project:view"));
        return mapping;
    }

    private record RoleSeed(String roleName, String roleDesc) {
    }

    private record PermissionSeed(String permissionName, String permissionType, String permissionDesc) {
    }
}
