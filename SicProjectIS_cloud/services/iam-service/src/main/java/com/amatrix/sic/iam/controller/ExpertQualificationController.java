package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.ExpertQualificationService;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;

@RestController
public class ExpertQualificationController {
    private final ExpertQualificationService service;

    public ExpertQualificationController(ExpertQualificationService service) {
        this.service = service;
    }

    @PostMapping("/api/expert-qualification/applications")
    ApiResponse<Map<String, Object>> submit(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.submit(actor(jwt), request));
    }

    @GetMapping("/api/expert-qualification/my")
    ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.mine(actor(jwt)));
    }

    @GetMapping("/api/admin/expert-qualification/applications")
    ApiResponse<Map<String, Object>> query(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.query(actor(jwt)));
    }

    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/dept-review")
    ApiResponse<Map<String, Object>> deptReview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.departmentReview(actor(jwt), applicationId, request));
    }

    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/science-review")
    ApiResponse<Map<String, Object>> scienceReview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.scienceReview(actor(jwt), applicationId, request));
    }

    private static Long actor(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
