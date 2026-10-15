package com.amatrix.sic.project.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.project.service.ProjectDepartmentService;
import com.amatrix.sic.security.ActorContextFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * PUT /api/projects/{projectId}/department 入口，从 JWT 构造操作人并委托部门服务；请求体为内部 record AssignDepartmentRequest。
 */
@RestController
public class ProjectDepartmentController {
    private final ProjectDepartmentService service;
    public ProjectDepartmentController(ProjectDepartmentService service) { this.service=service; }
    /**
     * 历史空部门项目的归属补充请求。
     * @param deptId IAM 部门 ID，必须存在且启用
     * @param reason 必填审计原因；该入口只允许填补空值，不用于变更已有项目部门
     */
    public record AssignDepartmentRequest(Long deptId, String reason) {}
    /**
     * {@code PUT /api/projects/{projectId}/department}：为历史上缺失所属部门的项目补充归属。
     * <p>要求 SYSTEM_ADMIN 或 SCIENCE_ADMIN；JSON deptId 为目标部门，reason 为必填审计原因。
     * <p>查询 IAM 确认部门存在且启用，并锁内复核项目原部门为空；已有归属返回 409，不允许借此任意调换部门。
     * <p>返回更新后的项目归属信息，同时记录审计和项目变更事件，使部门范围查询能够纳入该项目。
     */
    @PutMapping("/api/projects/{projectId}/department")
    ApiResponse<Map<String,Object>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody AssignDepartmentRequest request) {
        return ApiResponse.ok(service.assignMissing(projectId, ActorContextFactory.from(jwt), request.deptId(), request.reason()));
    }
}
