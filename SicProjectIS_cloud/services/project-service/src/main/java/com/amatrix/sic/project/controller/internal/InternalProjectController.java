package com.amatrix.sic.project.controller.internal;

import com.amatrix.sic.project.service.ProjectAuthorizationService;
import com.amatrix.sic.project.policy.ProjectAccessPolicy;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;

import java.util.Map;

/**
 * 内部单项/批量访问判定，以及 Workflow 专家授权同步和撤销接口。
 */
@RestController
@RequestMapping("/internal/projects")
public class InternalProjectController {
    private final ProjectAccessPolicy policy;
    private final ProjectAuthorizationService authorizations;
    private final com.amatrix.sic.project.service.ProjectFinanceReadinessService finance;
    public InternalProjectController(ProjectAccessPolicy policy, ProjectAuthorizationService authorizations,
            com.amatrix.sic.project.service.ProjectFinanceReadinessService finance) {
        this.policy = policy;
        this.authorizations = authorizations;
        this.finance = finance;
    }

    /**
     * {@code GET /internal/projects/{projectId}/finance-readiness}：检查目标财务节点是否已有匹配范围的有效财务经办人。
     * <p>要求 SCOPE_internal；projectId、moduleType、roundNo、taskNodeId 标识目标项目与运行时节点范围。
     * <p>返回财务配置就绪信息，供 Workflow 展示阻塞原因或办理提示；仅查询，不自动创建财务角色或授权。
     */
    @GetMapping("/{projectId}/finance-readiness")
    ApiResponse<Map<String,Object>> financeReadiness(@PathVariable Long projectId, @RequestParam String moduleType,
            @RequestParam Integer roundNo, @RequestParam String taskNodeId) {
        return ApiResponse.ok(finance.readiness(projectId,moduleType,roundNo,taskNodeId));
    }

    /**
     * {@code GET /internal/projects/{projectId}/access}：对一个用户和项目进行内部访问判定。
     * <p>要求 SCOPE_internal；userId、可选 deptId、roles 表示业务操作人，operation 缺省为 READ。
     * <p>返回 AccessDecision 的允许结果与判定说明；调用凭据的服务身份和被判定用户身份不同。
     * <p>这是服务间鉴权查询，不提供绕过 Gateway 的前端授权入口。
     */
    @GetMapping("/{projectId}/access")
    ApiResponse<AccessDecision> access(@PathVariable Long projectId, @RequestParam Long userId,
            @RequestParam(required = false) Long deptId, @RequestParam(defaultValue = "READ") String operation,
            @RequestParam(defaultValue = "") List<String> roles) {
        return ApiResponse.ok(policy.decide(userId, deptId, roles, projectId, operation));
    }

    /**
     * {@code POST /internal/projects/access-decisions}：批量判定同一操作人对多组项目操作的权限。
     * <p>要求 SCOPE_internal；JSON ProjectAccessBatchRequest 包含 actor 和 targets，目标可带模块、轮次及节点范围。
     * <p>返回 ProjectAccessResult 列表，通过 decisionId 和 projectId 关联目标，调用方必须同时校验关联关系。
     * <p>用于工作台筛选与批量授权检查，不改变权限或项目状态。
     */
    @PostMapping("/access-decisions")
    ApiResponse<List<ProjectAccessResult>> accessDecisions(@RequestBody ProjectAccessBatchRequest request) {
        return ApiResponse.ok(policy.decideBatch(request == null ? null : request.actor(),
                request == null ? List.of() : request.targets()));
    }

    /**
     * {@code POST /internal/projects/{projectId}/expert-grants/sync}：同步 Workflow 专家指派对应的临时项目授权。
     * <p>要求 SCOPE_internal；ExpertProjectGrantCommand 的 projectId 必须与路径一致，expertUserId 指定具有 EXPERT 角色的用户。
     * <p>按 moduleType、roundNo、taskNodeId 匹配授权；同范围已有效时返回 changed=false、ALREADY_ACTIVE，否则创建并返回 ACTIVE。
     * <p>授权变更与审计、Outbox 事件同事务写入；不在此创建评审 assignment。
     */
    @PostMapping("/{projectId}/expert-grants/sync")
    ApiResponse<Map<String, Object>> syncExpertGrant(@PathVariable Long projectId,
            @RequestBody ExpertProjectGrantCommand command) {
        return ApiResponse.ok(authorizations.syncExpertGrant(projectId, command));
    }

    /**
     * {@code POST /internal/projects/{projectId}/expert-grants/revoke}：按 Workflow 指派范围回收专家临时项目授权。
     * <p>要求 SCOPE_internal；请求项目与路径一致，并按 expertUserId、moduleType、roundNo、taskNodeId 定位授权。
     * <p>返回变更结果，重复调用无匹配记录时不重复产生有效变更；回收范围不扩展到其他批次授权。
     * <p>本人历史评分由个人评审接口读取，回收临时项目权限不删除这些历史记录。
     */
    @PostMapping("/{projectId}/expert-grants/revoke")
    ApiResponse<Map<String, Object>> revokeExpertGrant(@PathVariable Long projectId,
            @RequestBody ExpertProjectGrantCommand command) {
        return ApiResponse.ok(authorizations.revokeExpertGrant(projectId, command));
    }
}
