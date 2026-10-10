package com.amatrix.sic.project.controller;

import com.amatrix.sic.project.service.ProjectQueryService;
import com.amatrix.sic.project.service.ProjectAuthorizationService;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
@RequestMapping("/api/admin/projects")
public class ProjectAdminController {
    private final ProjectAuthorizationService service;
    public ProjectAdminController(ProjectAuthorizationService service) { this.service = service; }

    @GetMapping("/authorizations")
    ApiResponse<Map<String, Object>> authorizations(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.manageableProjects(ActorContextFactory.from(jwt),
                ProjectQueryService.number(jwt.getClaim("deptId"))));
    }
}
