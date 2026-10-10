package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.IamAdminService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import com.amatrix.sic.contracts.ApiResponse;

@RestController
@RequestMapping("/api/admin")
public class IamAdminController {
    private final IamAdminService service;

    public IamAdminController(IamAdminService service) {
        this.service = service;
    }

    @GetMapping("/users/roles")
    ApiResponse<Map<String, Object>> roleManagement() {
        return ApiResponse.ok(service.queryUsers(null, null, null, null, null));
    }

    @GetMapping("/users")
    ApiResponse<Map<String, Object>> users(@RequestParam(required = false) String username,
            @RequestParam(required = false) String realName, @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Boolean enabled, @RequestParam(required = false) String roleCode) {
        return ApiResponse.ok(service.queryUsers(username, realName, deptId, enabled, roleCode));
    }

    @GetMapping("/users/{userId}")
    ApiResponse<Map<String, Object>> user(@PathVariable Long userId) {
        return ApiResponse.ok(service.userDetail(userId));
    }

    @PutMapping("/users/{userId}/roles")
    ApiResponse<Map<String, Object>> roles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateRoles(actor(jwt), userId, request.get("roleCodes")));
    }

    @PatchMapping("/users/{userId}/status")
    ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) {
        return ApiResponse.ok(service.updateStatus(actor(jwt), userId, Boolean.TRUE.equals(request.get("enabled"))));
    }

    @GetMapping("/roles/permissions")
    ApiResponse<Map<String, Object>> matrix() {
        return ApiResponse.ok(service.permissionMatrix());
    }

    @GetMapping("/permissions")
    ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(service.permissions());
    }

    @PutMapping("/roles/{roleCode}/permissions")
    ApiResponse<Map<String, Object>> permissions(@AuthenticationPrincipal Jwt jwt, @PathVariable String roleCode,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateRolePermissions(actor(jwt), roleCode, request.get("permissionCodes")));
    }

    @GetMapping("/departments")
    ApiResponse<List<Map<String, Object>>> departments(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.departments(actor(jwt)));
    }

    @PostMapping("/departments")
    ApiResponse<Map<String, Object>> createDepartment(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.createDepartment(actor(jwt), request));
    }

    @PutMapping("/departments/{deptId}")
    ApiResponse<Map<String, Object>> updateDepartment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.updateDepartment(actor(jwt), deptId, request));
    }

    @DeleteMapping("/departments/{deptId}")
    ApiResponse<Void> deleteDepartment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId) {
        service.deleteDepartment(actor(jwt), deptId); return ApiResponse.ok(null);
    }

    @GetMapping("/departments/members")
    ApiResponse<Map<String, Object>> members(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(service.departmentMembers(actor(jwt), deptId));
    }

    @GetMapping("/departments/candidates")
    ApiResponse<Map<String, Object>> candidates(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.candidates(actor(jwt), keyword));
    }

    @PutMapping("/departments/{deptId}/members/{userId}")
    ApiResponse<Map<String, Object>> member(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId,
            @PathVariable Long userId, @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.upsertMember(actor(jwt), deptId, userId, request.get("roleCodes")));
    }

    @PatchMapping("/departments/members/{userId}/roles")
    ApiResponse<Map<String, Object>> memberRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateMemberRoles(actor(jwt), userId, request.get("roleCodes")));
    }

    @DeleteMapping("/departments/members/{userId}")
    ApiResponse<Map<String, Object>> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return ApiResponse.ok(service.removeMember(actor(jwt), userId));
    }

    private static Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
