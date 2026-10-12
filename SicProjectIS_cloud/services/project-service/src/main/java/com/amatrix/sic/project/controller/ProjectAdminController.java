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

/**
 * GET /api/admin/projects/authorizations，列出当前管理员可管理的项目授权。
 */
@RestController
@RequestMapping("/api/admin/projects")
public class ProjectAdminController {
    private final ProjectAuthorizationService service;
    public ProjectAdminController(ProjectAuthorizationService service) { this.service = service; }

    /**
     * {@code GET /api/admin/projects/authorizations}：加载项目授权管理页的可管理项目及授权类型统计。
     * <p>要求 PERM_PROJECT_GRANT_VIEW；系统/科研管理员查询全局，部门管理员按 JWT 部门限制项目范围。
     * <p>返回 projects、grantTypeCounts；这是管理入口的数据查询，各项目具体变更能力仍由授权服务单独校验。
     */
    @GetMapping("/authorizations")
    ApiResponse<Map<String, Object>> authorizations(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.manageableProjects(ActorContextFactory.from(jwt),
                ProjectQueryService.number(jwt.getClaim("deptId"))));
    }
}
