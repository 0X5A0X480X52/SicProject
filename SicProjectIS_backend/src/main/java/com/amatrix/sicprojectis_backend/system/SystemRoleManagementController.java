package com.amatrix.sicprojectis_backend.system;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserDetailResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserQueryResponse;
import com.amatrix.sicprojectis_backend.system.dto.UpdateUserRolesRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpdateUserStatusRequest;
import com.amatrix.sicprojectis_backend.system.dto.UserRoleManagementResponse;
import com.amatrix.sicprojectis_backend.system.dto.UserRoleUpdateResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class SystemRoleManagementController {
    private final SystemRoleManagementService systemRoleManagementService;

    public SystemRoleManagementController(SystemRoleManagementService systemRoleManagementService) {
        this.systemRoleManagementService = systemRoleManagementService;
    }

    @GetMapping("/roles")
    public ApiResponse<UserRoleManagementResponse> roleManagement(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(systemRoleManagementService.getRoleManagement(currentUser));
    }

    @GetMapping
    public ApiResponse<AdminUserQueryResponse> users(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String roleCode) {
        return ApiResponse.ok(systemRoleManagementService.queryUsers(currentUser, username, realName, deptId, enabled, roleCode));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> userDetail(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId) {
        return ApiResponse.ok(systemRoleManagementService.getUserDetail(currentUser, userId));
    }

    @PutMapping("/{userId}/roles")
    public ApiResponse<UserRoleUpdateResponse> updateUserRoles(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId,
            @RequestBody UpdateUserRolesRequest request) {
        return ApiResponse.ok(systemRoleManagementService.updateUserRoles(currentUser, userId, request));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserDetailResponse> updateUserStatus(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId,
            @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(systemRoleManagementService.updateUserStatus(currentUser, userId, request));
    }
}
