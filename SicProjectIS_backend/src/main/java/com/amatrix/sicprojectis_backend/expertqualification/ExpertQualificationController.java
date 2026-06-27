package com.amatrix.sicprojectis_backend.expertqualification;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ExpertQualificationApplicationQueryResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ExpertQualificationApplicationResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.MyExpertQualificationResponse;
import com.amatrix.sicprojectis_backend.expertqualification.dto.ReviewExpertQualificationRequest;
import com.amatrix.sicprojectis_backend.expertqualification.dto.SubmitExpertQualificationRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpertQualificationController {
    private final ExpertQualificationService expertQualificationService;

    public ExpertQualificationController(ExpertQualificationService expertQualificationService) {
        this.expertQualificationService = expertQualificationService;
    }

    @PostMapping("/api/expert-qualification/applications")
    public ApiResponse<ExpertQualificationApplicationResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody SubmitExpertQualificationRequest request) {
        return ApiResponse.ok(expertQualificationService.submit(currentUser, request));
    }

    @GetMapping("/api/expert-qualification/my")
    public ApiResponse<MyExpertQualificationResponse> myApplications(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(expertQualificationService.myApplications(currentUser));
    }

    @GetMapping("/api/admin/expert-qualification/applications")
    public ApiResponse<ExpertQualificationApplicationQueryResponse> adminApplications(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(expertQualificationService.queryAdminApplications(currentUser));
    }

    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/dept-review")
    public ApiResponse<ExpertQualificationApplicationResponse> deptReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long applicationId,
            @RequestBody ReviewExpertQualificationRequest request) {
        return ApiResponse.ok(expertQualificationService.reviewByDept(currentUser, applicationId, request));
    }

    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/science-review")
    public ApiResponse<ExpertQualificationApplicationResponse> scienceReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long applicationId,
            @RequestBody ReviewExpertQualificationRequest request) {
        return ApiResponse.ok(expertQualificationService.reviewByScience(currentUser, applicationId, request));
    }
}
