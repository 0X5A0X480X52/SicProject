package com.amatrix.sicprojectis_backend.system;

import java.time.LocalDateTime;
import java.util.List;

import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.RolePermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.system.entity.Permission;
import com.amatrix.sicprojectis_backend.system.entity.Role;
import com.amatrix.sicprojectis_backend.system.entity.RolePermission;
import com.amatrix.sicprojectis_backend.system.entity.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
@Order(20)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final BootstrapAdminProperties properties;
    private final AppUserDao appUserDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            AppUserDao appUserDao,
            RoleDao roleDao,
            UserRoleDao userRoleDao,
            PermissionDao permissionDao,
            RolePermissionDao rolePermissionDao,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.appUserDao = appUserDao;
        this.roleDao = roleDao;
        this.userRoleDao = userRoleDao;
        this.permissionDao = permissionDao;
        this.rolePermissionDao = rolePermissionDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        Role systemAdminRole = ensureSystemAdminRole();
        ensureSystemAdminPermissions(systemAdminRole);
        ensureBootstrapAdminUser(systemAdminRole);
    }

    private Role ensureSystemAdminRole() {
        Role role = roleDao.selectByCode(SYSTEM_ADMIN);
        if (role != null) {
            if (!Boolean.TRUE.equals(role.getEnabled())) {
                role.setEnabled(true);
                roleDao.updateById(role);
            }
            return role;
        }

        Role created = new Role();
        created.setRoleCode(SYSTEM_ADMIN);
        created.setRoleName("System Admin");
        created.setRoleDesc("Bootstrap super administrator");
        created.setEnabled(true);
        roleDao.insert(created);
        return created;
    }

    private void ensureSystemAdminPermissions(Role systemAdminRole) {
        List<Permission> permissions = permissionDao.selectAll();
        for (Permission permission : permissions) {
            RolePermission existing = rolePermissionDao.selectByRoleIdAndPermissionId(
                    systemAdminRole.getRoleId(),
                    permission.getPermissionId());
            if (existing == null) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(systemAdminRole.getRoleId());
                rolePermission.setPermissionId(permission.getPermissionId());
                rolePermissionDao.insert(rolePermission);
            }
        }
    }

    private void ensureBootstrapAdminUser(Role systemAdminRole) {
        AppUser user = appUserDao.selectByUsername(properties.getUsername());
        if (user == null) {
            user = new AppUser();
            user.setUsername(properties.getUsername());
            user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
            user.setRealName(properties.getRealName());
            user.setDeptId(null);
            user.setPhone(null);
            user.setEmail(null);
            user.setEnabled(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            appUserDao.insert(user);
        } else {
            boolean changed = false;
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                user.setEnabled(true);
                changed = true;
            }
            if (properties.getRealName() != null && !properties.getRealName().isBlank()
                    && !properties.getRealName().equals(user.getRealName())) {
                user.setRealName(properties.getRealName());
                changed = true;
            }
            if (!passwordEncoder.matches(properties.getPassword(), user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
                changed = true;
            }
            if (changed) {
                appUserDao.updateById(user);
            }
        }

        if (userRoleDao.selectByUserIdAndRoleId(user.getUserId(), systemAdminRole.getRoleId()) == null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(systemAdminRole.getRoleId());
            userRole.setAssignedAt(LocalDateTime.now());
            userRoleDao.insert(userRole);
        }
    }
}
