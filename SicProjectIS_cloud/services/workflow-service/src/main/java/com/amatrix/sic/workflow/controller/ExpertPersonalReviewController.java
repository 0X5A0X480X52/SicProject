package com.amatrix.sic.workflow.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.service.review.ExpertPersonalReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expert-reviews")
public class ExpertPersonalReviewController {
    private final ExpertPersonalReviewService service;
    public ExpertPersonalReviewController(ExpertPersonalReviewService service) {this.service=service;}
    @GetMapping("/my-assignments")
    ApiResponse<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.list(ActorContextFactory.from(jwt),status,page,pageSize));
    }
    @GetMapping("/my-assignments/{assignmentId}")
    ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable Long assignmentId) {
        return ApiResponse.ok(service.detail(ActorContextFactory.from(jwt),assignmentId));
    }
}
