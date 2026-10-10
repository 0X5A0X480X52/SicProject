package com.amatrix.sic.project;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.project.ProjectDtos.StartProjectApplicationRequest;
import com.amatrix.sic.project.ProjectDtos.StartProjectApplicationResponse;

@RestController
public class ProjectController {
    private final ProjectQueryService queries;
    private final ProjectApplicationSagaService sagas;

    public ProjectController(ProjectQueryService queries, ProjectApplicationSagaService sagas) {
        this.queries = queries; this.sagas = sagas;
    }

    @GetMapping("/api/projects")
    ApiResponse<List<Map<String, Object>>> projects(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(queries.accessibleProjects(Long.valueOf(jwt.getSubject()),
                ProjectQueryService.number(jwt.getClaim("deptId")), ProjectQueryService.roles(jwt.getClaim("roles"))));
    }

    @PostMapping("/api/project-applications/start")
    ApiResponse<StartProjectApplicationResponse> start(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody StartProjectApplicationRequest request) {
        return ApiResponse.ok(sagas.start(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("realName"),
                ProjectQueryService.number(jwt.getClaim("deptId")), jwt.getClaimAsString("deptName"),
                authorization, request));
    }
}
