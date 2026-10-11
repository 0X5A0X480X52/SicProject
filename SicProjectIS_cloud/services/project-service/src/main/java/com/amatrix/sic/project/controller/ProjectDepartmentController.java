package com.amatrix.sic.project.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.project.service.ProjectDepartmentService;
import com.amatrix.sic.security.ActorContextFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProjectDepartmentController {
    private final ProjectDepartmentService service;
    public ProjectDepartmentController(ProjectDepartmentService service) { this.service=service; }
    public record AssignDepartmentRequest(Long deptId, String reason) {}
    @PutMapping("/api/projects/{projectId}/department")
    ApiResponse<Map<String,Object>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody AssignDepartmentRequest request) {
        return ApiResponse.ok(service.assignMissing(projectId, ActorContextFactory.from(jwt), request.deptId(), request.reason()));
    }
}
