package com.amatrix.sicprojectis_backend.system;

import java.util.List;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberCandidateResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberQueryResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentMemberResponse;
import com.amatrix.sicprojectis_backend.system.dto.DepartmentOptionResponse;
import com.amatrix.sicprojectis_backend.system.dto.SaveDepartmentRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpdateDepartmentMemberRolesRequest;
import com.amatrix.sicprojectis_backend.system.dto.UpsertDepartmentMemberRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentMemberManagementController {
    private final DepartmentMemberManagementService departmentMemberManagementService;

    public DepartmentMemberManagementController(DepartmentMemberManagementService departmentMemberManagementService) {
        this.departmentMemberManagementService = departmentMemberManagementService;
    }

    @GetMapping
    public ApiResponse<List<DepartmentOptionResponse>> departments(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(departmentMemberManagementService.departments(currentUser));
    }

    @PostMapping
    public ApiResponse<DepartmentOptionResponse> createDepartment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.ok(departmentMemberManagementService.createDepartment(currentUser, request));
    }

    @PutMapping("/{deptId}")
    public ApiResponse<DepartmentOptionResponse> updateDepartment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long deptId,
            @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.ok(departmentMemberManagementService.updateDepartment(currentUser, deptId, request));
    }

    @DeleteMapping("/{deptId}")
    public ApiResponse<Void> deleteDepartment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long deptId) {
        departmentMemberManagementService.deleteDepartment(currentUser, deptId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/members")
    public ApiResponse<DepartmentMemberQueryResponse> members(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(departmentMemberManagementService.members(currentUser, deptId));
    }

    @GetMapping("/candidates")
    public ApiResponse<DepartmentMemberCandidateResponse> candidates(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(departmentMemberManagementService.candidates(currentUser, keyword));
    }

    @PutMapping("/{deptId}/members/{userId}")
    public ApiResponse<DepartmentMemberResponse> upsertMember(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long deptId,
            @PathVariable Long userId,
            @RequestBody UpsertDepartmentMemberRequest request) {
        return ApiResponse.ok(departmentMemberManagementService.upsertMember(currentUser, deptId, userId, request));
    }

    @PatchMapping("/members/{userId}/roles")
    public ApiResponse<DepartmentMemberResponse> updateRoles(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId,
            @RequestBody UpdateDepartmentMemberRolesRequest request) {
        return ApiResponse.ok(departmentMemberManagementService.updateRoles(currentUser, userId, request));
    }

    @DeleteMapping("/members/{userId}")
    public ApiResponse<DepartmentMemberResponse> removeMember(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId) {
        return ApiResponse.ok(departmentMemberManagementService.removeMember(currentUser, userId));
    }
}