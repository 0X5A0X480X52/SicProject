package com.amatrix.sicprojectis_backend.system;

import java.util.List;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.system.dto.PermissionDefinitionResponse;
import com.amatrix.sicprojectis_backend.system.dto.RolePermissionMatrixResponse;
import com.amatrix.sicprojectis_backend.system.dto.RolePermissionUpdateResponse;
import com.amatrix.sicprojectis_backend.system.dto.UpdateRolePermissionsRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RolePermissionManagementController {
    private final RolePermissionManagementService rolePermissionManagementService;

    public RolePermissionManagementController(RolePermissionManagementService rolePermissionManagementService) {
        this.rolePermissionManagementService = rolePermissionManagementService;
    }

    @GetMapping("/roles/permissions")
    public ApiResponse<RolePermissionMatrixResponse> matrix(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(rolePermissionManagementService.getMatrix(currentUser));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDefinitionResponse>> permissions(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(rolePermissionManagementService.getPermissions(currentUser));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public ApiResponse<RolePermissionUpdateResponse> update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String roleCode,
            @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.ok(rolePermissionManagementService.updateRolePermissions(currentUser, roleCode, request));
    }
}
