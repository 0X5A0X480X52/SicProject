package com.amatrix.sic.project.controller;

import com.amatrix.sic.project.service.ProjectQueryService;
import com.amatrix.sic.project.service.saga.ProjectApplicationSagaService;
import com.amatrix.sic.project.dto.ProjectDtos;

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
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationRequest;
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationResponse;

/**
 * 项目列表查询和 POST /api/project-applications/start 入口，将 JWT 用户/部门及原授权 Header 交给查询/Saga 服务。
 */
@RestController
public class ProjectController {
    private final ProjectQueryService queries;
    private final ProjectApplicationSagaService sagas;

    public ProjectController(ProjectQueryService queries, ProjectApplicationSagaService sagas) {
        this.queries = queries; this.sagas = sagas;
    }

    /**
     * {@code GET /api/projects}：列出当前账号有权读取的项目主数据。
     * <p>从 JWT 提取用户、部门与角色，由 Project 访问规则筛选；负责人、成员、管理员范围及有效授权分别参与判定。
     * <p>返回项目摘要列表；普通人员仅与项目同部门并不会自动获得访问权限，此接口也不返回工作流节点明细。
     */
    @GetMapping("/api/projects")
    ApiResponse<List<Map<String, Object>>> projects(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(queries.accessibleProjects(Long.valueOf(jwt.getSubject()),
                ProjectQueryService.number(jwt.getClaim("deptId")), ProjectQueryService.roles(jwt.getClaim("roles"))));
    }

    /**
     * {@code POST /api/project-applications/start}：创建项目并启动申请模块，返回申请业务入口。
     * <p>JSON 使用 StartProjectApplicationRequest 的项目和申请字段；操作人取 JWT，Authorization 请求头用于远程 Workflow 调用。
     * <p>创建前查询 IAM 当前启用账号及有效所属部门，不能依赖过期 JWT 部门或请求体自行指定归属。
     * <p>通过 Saga 分阶段创建项目、创建工作流、确认，失败尝试补偿；返回 projectId、moduleInstanceId、初始状态契约。
     * <p>近期相同申请可复用已完成结果，处理中返回 409；定时恢复目前仅覆盖 WORKFLOW_CREATED 阶段。
     */
    @PostMapping("/api/project-applications/start")
    ApiResponse<StartProjectApplicationResponse> start(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody StartProjectApplicationRequest request) {
        return ApiResponse.ok(sagas.start(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("realName"),
                ProjectQueryService.number(jwt.getClaim("deptId")), jwt.getClaimAsString("deptName"),
                authorization, request));
    }
}
