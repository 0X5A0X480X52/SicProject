package com.amatrix.sicprojectis_backend.security;

import org.springframework.stereotype.Service;

/**
 * Compatibility facade for existing SpEL expressions and application code.
 * New code should depend on the domain-specific policies directly.
 */
@Service("permissionService")
public class PermissionService {
    private final GlobalPermissionPolicy globalPermissionPolicy;
    private final ProjectAccessPolicy projectAccessPolicy;
    private final WorkflowOperationPolicy workflowOperationPolicy;

    public PermissionService(
            GlobalPermissionPolicy globalPermissionPolicy,
            ProjectAccessPolicy projectAccessPolicy,
            WorkflowOperationPolicy workflowOperationPolicy) {
        this.globalPermissionPolicy = globalPermissionPolicy;
        this.projectAccessPolicy = projectAccessPolicy;
        this.workflowOperationPolicy = workflowOperationPolicy;
    }

    public boolean hasRole(Long userId, String roleCode) {
        return globalPermissionPolicy.hasRole(userId, roleCode);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return globalPermissionPolicy.hasPermission(userId, permissionCode);
    }

    public boolean canAccessProject(Long userId, Long projectId) {
        return projectAccessPolicy.canAccess(userId, projectId);
    }

    public boolean canOperateModuleNode(Long userId, Long moduleInstanceId) {
        return workflowOperationPolicy.canOperate(userId, moduleInstanceId);
    }
}
