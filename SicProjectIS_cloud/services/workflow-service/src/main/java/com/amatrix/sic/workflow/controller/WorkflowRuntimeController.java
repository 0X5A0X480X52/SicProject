package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService;
import com.amatrix.sic.workflow.dto.WorkflowDtos;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.dto.WorkflowDtos.StartModuleRequest;
import com.amatrix.sic.workflow.dto.WorkflowDtos.TransitionRequest;

/**
 * 模块启动、普通/legacy 状态迁移、runtime-view 与 workflow-workbench/items API。
 */
@RestController
public class WorkflowRuntimeController {
    private final WorkflowRuntimeService service;
    public WorkflowRuntimeController(WorkflowRuntimeService service){this.service=service;}

    /**
     * {@code POST /api/projects/{projectId}/module-instances}：为已有项目启动指定业务模块实例。
     * <p>要求 SCIENCE_ADMIN 或 SYSTEM_ADMIN；JSON moduleType 仅允许通过此入口启动 CONTRACT 或 ACCEPTANCE，服务另检查项目启动权限。
     * <p>APPLICATION 明确拒绝通过此入口启动，初次申请必须使用 project-applications/start 的 Saga；同类模块已存在返回 409。
     * <p>返回 StateTransitionContract，包含新模块运行状态。
     * <p>自动后继模块由流程规则创建，调用方不能把此接口作为绕过前置业务状态的补建手段。
     */
    @PostMapping("/api/projects/{projectId}/module-instances")
    ApiResponse<StateTransitionContract> start(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody StartModuleRequest request){return ApiResponse.ok(service.start(projectId,request.moduleType(),ActorContextFactory.from(jwt)));}

    /**
     * {@code POST /api/module-instances/{moduleInstanceId}/transitions}：提交当前节点业务动作，保存表单和材料引用并推进状态机。
     * <p>JSON TransitionRequest 包含 eventType、expectedSeq、result、remark、materialVersionIds、formCode、nodeFormData。
     * <p>expectedSeq 是客户端观察到的模块序号；材料使用具体版本 ID，节点表单使用注册代码和对应业务结构。
     * <p>兼容省略 expectedSeq 时使用服务预读序号，锁内仍复核；新调用方应明确传入 runtime-view 的最新 currentSeq。
     * <p>先在事务外预留材料，再锁内复核当前节点、序号及操作资格，保存迁移、业务数据和事件；竞争或过期序号返回 409。
     * <p>返回新的 StateTransitionContract；迁移确认与通知按事件处理，不能仅凭一次推送判断流程已完成。
     */
    @PostMapping("/api/module-instances/{moduleInstanceId}/transitions")
    ApiResponse<StateTransitionContract> transition(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId,
            @RequestBody TransitionRequest request){return ApiResponse.ok(service.transition(moduleInstanceId,ActorContextFactory.from(jwt),request));}

    /**
     * {@code POST /api/legacy/module-instances/{moduleInstanceId}/transitions}：保留旧调用路径的节点迁移兼容入口。
     * <p>请求、鉴权、材料预留、序号校验及返回值均与 /api/module-instances/{moduleInstanceId}/transitions 相同。
     * <p>直接调用相同业务服务，不跳过新接口的业务约束；新调用方应使用正式迁移路径。
     */
    @PostMapping("/api/legacy/module-instances/{moduleInstanceId}/transitions")
    ApiResponse<StateTransitionContract> legacyTransition(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId,
            @RequestBody TransitionRequest request){return ApiResponse.ok(service.transition(moduleInstanceId,ActorContextFactory.from(jwt),request));}

    /**
     * {@code GET /api/module-instances/{moduleInstanceId}/runtime-view}：读取模块的当前运行视图及操作上下文。
     * <p>moduleInstanceId 为运行实例 ID，服务校验当前人员的项目流程读取权限。
     * <p>返回 context（含 currentSeq/currentRoundNo）、canOperate、availableTransitions、materialRequirements、nodeForms、openTasks、history、taskConfiguration。
     * <p>材料要求及表单定义是配置元数据，实际文件和表单内容需通过对应接口读取；财务节点附当前配置就绪信息。
     * <p>提交前应重新读取并使用最新 seq；模块 finished 与整个项目生命周期终态不同。
     */
    @GetMapping("/api/module-instances/{moduleInstanceId}/runtime-view")
    ApiResponse<Map<String,Object>> view(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId){return ApiResponse.ok(service.runtimeView(moduleInstanceId,ActorContextFactory.from(jwt)));}

    /**
     * {@code GET /api/workflow-workbench/items}：读取旧版工作台使用的当前可办理模块条目集合。
     * <p>当前账号来自 JWT，服务按实际节点操作资格筛选，条目 todo、canOperate 均为 true；返回数组，不提供视图和分页参数。
     * <p>按待办、经办、可见和已完成分类的新页面使用 /api/workflow-workbench/query，避免自行推断授权范围。
     */
    @GetMapping("/api/workflow-workbench/items")
    ApiResponse<List<Map<String,Object>>> workbench(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.workbench(ActorContextFactory.from(jwt)));}
}
