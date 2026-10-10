package com.amatrix.sic.project.controller;

import com.amatrix.sic.project.service.ProjectAuthorizationService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
@RequestMapping("/api/projects")
public class ProjectAuthorizationController {
    private final ProjectAuthorizationService service;

    public ProjectAuthorizationController(ProjectAuthorizationService service) { this.service = service; }

    @GetMapping("/{projectId}/authorization")
    ApiResponse<Map<String, Object>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId) {
        return ApiResponse.ok(service.detail(projectId, actor(jwt)));
    }

    @PutMapping("/{projectId}/leader")
    ApiResponse<Map<String, Object>> leader(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.changeLeader(projectId, actor(jwt), number(request.get("userId")),
                string(request.get("reason"))));
    }

    @PutMapping("/{projectId}/members")
    ApiResponse<Map<String, Object>> member(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.upsertMember(projectId, actor(jwt), number(request.get("userId")),
                string(request.get("responsibility"))));
    }

    @PutMapping("/{projectId}/members/batch")
    ApiResponse<Map<String, Object>> members(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.upsertMembers(projectId, actor(jwt), numbers(request.get("userIds")),
                string(request.get("responsibility"))));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    ApiResponse<Map<String, Object>> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @PathVariable Long userId) {
        return ApiResponse.ok(service.removeMember(projectId, actor(jwt), userId));
    }

    @PostMapping("/{projectId}/expert-grants")
    ApiResponse<Map<String, Object>> expert(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_MODULE_EXPERT_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/expert-grants/batch")
    ApiResponse<Map<String, Object>> experts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_MODULE_EXPERT_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/finance-grants")
    ApiResponse<Map<String, Object>> finance(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_FINANCE_HANDLER_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/finance-grants/batch")
    ApiResponse<Map<String, Object>> finances(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_FINANCE_HANDLER_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/proxy-grants")
    ApiResponse<Map<String, Object>> proxy(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_PROXY_RECORDER_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/proxy-grants/batch")
    ApiResponse<Map<String, Object>> proxies(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_PROXY_RECORDER_ASSIGNMENT", request));
    }

    @PostMapping("/{projectId}/grants/{grantId}/revoke")
    ApiResponse<Map<String, Object>> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @PathVariable Long grantId, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.revoke(projectId, actor(jwt), grantId, string(request.get("reason"))));
    }

    @PostMapping("/{projectId}/grants/revoke-batch")
    ApiResponse<Map<String, Object>> revokeBatch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.revokeBatch(projectId, actor(jwt), numbers(request.get("grantIds")),
                string(request.get("reason"))));
    }

    private Map<String, Object> grant(Long projectId, Jwt jwt, String role, Map<String, Object> request) {
        return service.assignGrant(projectId, actor(jwt), role, number(request.get("userId")),
                string(request.get("moduleType")), integer(request.get("roundNo")),
                string(request.get("taskNodeId")), string(request.get("reason")));
    }

    private Map<String, Object> grants(Long projectId, Jwt jwt, String role, Map<String, Object> request) {
        return service.assignGrants(projectId, actor(jwt), role, numbers(request.get("userIds")),
                string(request.get("moduleType")), integer(request.get("roundNo")),
                string(request.get("taskNodeId")), string(request.get("reason")));
    }

    private static ActorContext actor(Jwt jwt) { return ActorContextFactory.from(jwt); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Long number(Object value) {
        if (value == null) return null; return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value));
    }
    private static Integer integer(Object value) {
        if (value == null) return null; return value instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(value));
    }
    private static List<Long> numbers(Object value) {
        if (!(value instanceof List<?> list)) return List.of(); return list.stream().map(ProjectAuthorizationController::number).toList();
    }
}
