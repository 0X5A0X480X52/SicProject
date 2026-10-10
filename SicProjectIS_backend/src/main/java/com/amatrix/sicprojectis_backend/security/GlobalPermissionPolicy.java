package com.amatrix.sicprojectis_backend.security;

import java.util.List;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;

/**
 * Evaluates global roles and permission codes against the authoritative IAM
 * tables. It deliberately has no dependency on SecurityContextHolder.
 */
@Service("globalPermissionPolicy")
public class GlobalPermissionPolicy {
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionDao permissionDao;

    public GlobalPermissionPolicy(UserRoleDetailViewDao userRoleDetailViewDao, PermissionDao permissionDao) {
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionDao = permissionDao;
    }

    public boolean hasRole(Long userId, String roleCode) {
        return roleCode != null && roles(userId).contains(roleCode);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return permissionDao.countPermissionByUserId(userId, permissionCode) > 0;
    }

    public List<String> roles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleDetailViewDao.selectByUserId(userId).stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
