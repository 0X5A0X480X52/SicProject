package com.amatrix.sicprojectis_backend.project;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.project.dto.StartProjectApplicationRequest;
import com.amatrix.sicprojectis_backend.project.dto.StartProjectApplicationResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/project-applications")
public class ProjectApplicationStartController {
    private final ProjectApplicationStartService service;

    public ProjectApplicationStartController(ProjectApplicationStartService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public ApiResponse<StartProjectApplicationResponse> start(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody StartProjectApplicationRequest request) {
        return ApiResponse.ok(service.start(user, request));
    }
}
