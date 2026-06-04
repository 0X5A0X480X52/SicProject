package com.amatrix.sicprojectis_backend.project;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.project.dto.AdminProjectAuthorizationsResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectAuthorizationController {
    private final ProjectAuthorizationService projectAuthorizationService;

    public AdminProjectAuthorizationController(ProjectAuthorizationService projectAuthorizationService) {
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @GetMapping("/authorizations")
    public ApiResponse<AdminProjectAuthorizationsResponse> authorizations(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(projectAuthorizationService.listManageableProjects(currentUser));
    }
}
