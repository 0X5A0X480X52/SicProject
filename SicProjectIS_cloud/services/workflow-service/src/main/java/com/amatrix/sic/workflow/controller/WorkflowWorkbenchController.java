package com.amatrix.sic.workflow.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.service.runtime.WorkflowWorkbenchQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/workflow-workbench/query 入口，解析视图、模块类型、关键字与分页参数并委托工作台查询服务。
 */
@RestController
public class WorkflowWorkbenchController {
    private final WorkflowWorkbenchQueryService service;
    public WorkflowWorkbenchController(WorkflowWorkbenchQueryService service) {this.service=service;}
    /**
     * {@code GET /api/workflow-workbench/query}：分页查询工作台的待办、经办、可见或已结束模块。
     * <p>view 支持 TODO、OPERATED、VISIBLE、FINISHED，缺省 VISIBLE；moduleType 可选 APPLICATION、CONTRACT、ACCEPTANCE，keyword 可选。
     * <p>page 缺省 1、范围 1..1000；pageSize 缺省 10、范围 1..100，keyword 最多 200 字符。
     * <p>服务分批读取候选后按当前办理或项目流程读取权限筛选，返回 items、page、pageSize、hasMore 及条目的 todo、canOperate。
     * <p>FINISHED 表示模块结束，不等于整个项目成功结题；经办历史也不永久赋予项目权限，不提供未经权限筛选的 total。
     */
    @GetMapping("/api/workflow-workbench/query")
    ApiResponse<Map<String,Object>> query(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="VISIBLE") String view,@RequestParam(required=false) String moduleType,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.query(ActorContextFactory.from(jwt),view,moduleType,keyword,page,pageSize));
    }
}
