package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.review.ExpertReviewService;
import com.amatrix.sic.workflow.dto.ExpertReviewDtos;

import static com.amatrix.sic.workflow.dto.ExpertReviewDtos.*;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
@RequestMapping("/api/expert-reviews")
public class ExpertReviewController {
    private final ExpertReviewService service;
    public ExpertReviewController(ExpertReviewService service) { this.service = service; }

    @PostMapping("/batches")
    ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateBatchRequest request) {
        return ApiResponse.ok(service.create(ActorContextFactory.from(jwt), request));
    }
    @GetMapping("/batches/{batchId}")
    ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.ok(service.detail(ActorContextFactory.from(jwt), batchId));
    }
    @PostMapping("/batches/{batchId}/assignments")
    ApiResponse<Map<String,Object>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId,
            @RequestBody AssignExpertRequest request) {
        return ApiResponse.ok(service.assign(ActorContextFactory.from(jwt), batchId, request));
    }
    @PostMapping("/assignments/{assignmentId}/scores")
    ApiResponse<Map<String,Object>> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId,
            @RequestBody SubmitScoreRequest request) {
        return ApiResponse.ok(service.submit(ActorContextFactory.from(jwt), assignmentId, request));
    }
    @DeleteMapping("/assignments/{assignmentId}")
    ApiResponse<Map<String,Object>> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId) {
        return ApiResponse.ok(service.remove(ActorContextFactory.from(jwt), assignmentId));
    }
    @PostMapping("/assignments/{assignmentId}/remove")
    ApiResponse<Map<String,Object>> removeFallback(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId) {
        return remove(jwt, assignmentId);
    }
}
