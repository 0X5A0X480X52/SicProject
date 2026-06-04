package com.amatrix.sicprojectis_backend.project;

import java.util.List;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.project.dto.AssignProjectExpertRequest;
import com.amatrix.sicprojectis_backend.project.dto.AssignProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchProjectMemberRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchRevokeProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.ChangeProjectLeaderRequest;
import com.amatrix.sicprojectis_backend.project.dto.ProjectAuthorizationDetailResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectAuthorizationMutationResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectSummaryResponse;
import com.amatrix.sicprojectis_backend.project.dto.RevokeProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.UpsertProjectMemberRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectAuthorizationController {
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectAuthorizationController(ProjectAuthorizationService projectAuthorizationService) {
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @GetMapping
    public ApiResponse<List<ProjectSummaryResponse>> listProjects(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(projectAuthorizationService.listAccessibleProjects(currentUser));
    }

    @GetMapping("/{projectId}/authorization")
    public ApiResponse<ProjectAuthorizationDetailResponse> authorizationDetail(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId) {
        return ApiResponse.ok(projectAuthorizationService.getAuthorizationDetail(currentUser, projectId));
    }

    @PutMapping("/{projectId}/leader")
    public ApiResponse<ProjectAuthorizationDetailResponse> changeLeader(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody ChangeProjectLeaderRequest request) {
        return ApiResponse.ok(projectAuthorizationService.changeLeader(currentUser, projectId, request));
    }

    @PutMapping("/{projectId}/members")
    public ApiResponse<ProjectAuthorizationDetailResponse> upsertMember(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody UpsertProjectMemberRequest request) {
        return ApiResponse.ok(projectAuthorizationService.upsertMember(currentUser, projectId, request));
    }

    @PutMapping("/{projectId}/members/batch")
    public ApiResponse<ProjectAuthorizationMutationResponse> upsertMembers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody BatchProjectMemberRequest request) {
        return ApiResponse.ok(projectAuthorizationService.upsertMembers(currentUser, projectId, request));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ApiResponse<ProjectAuthorizationDetailResponse> removeMember(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        return ApiResponse.ok(projectAuthorizationService.removeMember(currentUser, projectId, userId));
    }

    @PostMapping("/{projectId}/expert-grants")
    public ApiResponse<ProjectAuthorizationDetailResponse> assignExpert(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody AssignProjectExpertRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignExpert(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/expert-grants/batch")
    public ApiResponse<ProjectAuthorizationMutationResponse> assignExperts(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody BatchProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignExperts(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/finance-grants")
    public ApiResponse<ProjectAuthorizationDetailResponse> assignFinance(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody AssignProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignFinance(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/finance-grants/batch")
    public ApiResponse<ProjectAuthorizationMutationResponse> assignFinances(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody BatchProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignFinances(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/proxy-grants")
    public ApiResponse<ProjectAuthorizationDetailResponse> assignProxy(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody AssignProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignProxy(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/proxy-grants/batch")
    public ApiResponse<ProjectAuthorizationMutationResponse> assignProxies(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody BatchProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.assignProxies(currentUser, projectId, request));
    }

    @PostMapping("/{projectId}/grants/{grantId}/revoke")
    public ApiResponse<ProjectAuthorizationDetailResponse> revokeGrant(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @PathVariable Long grantId,
            @RequestBody RevokeProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.revokeGrant(currentUser, projectId, grantId, request));
    }

    @PostMapping("/{projectId}/grants/revoke-batch")
    public ApiResponse<ProjectAuthorizationMutationResponse> revokeGrants(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long projectId,
            @RequestBody BatchRevokeProjectGrantRequest request) {
        return ApiResponse.ok(projectAuthorizationService.revokeGrants(currentUser, projectId, request));
    }
}
