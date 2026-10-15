package com.amatrix.sic.workflow.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;
import java.util.Map;

/**
 * Feign 调 Project 单项/批量访问判定、专家授权同步与撤销接口。
 */
@FeignClient(name="project-service",contextId="workflowProjectAccessClient")
public interface ProjectAccessClient {
    /**
     * {@code GET /internal/projects/{projectId}/finance-readiness}：查询目标财务节点是否有有效经办人。
     * <p>Authorization 使用 workflow-service 内部 Bearer token；moduleType、roundNo、taskNodeId 必须来自当前模块上下文。
     * <p>返回 ready、handlerCount、status、message，供 runtime-view 展示配置状态；查询不自动创建财务角色、项目授权或待办。
     */
    @GetMapping("/internal/projects/{projectId}/finance-readiness")
    ApiResponse<Map<String,Object>> financeReadiness(@RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId, @RequestParam String moduleType, @RequestParam Integer roundNo,
            @RequestParam String taskNodeId);

    /**
     * {@code GET /internal/projects/{projectId}/access}：判定一个用户对项目操作是否获准。
     * <p>Authorization 表示调用服务身份；userId、deptId、roles 表示被判定业务操作人，两种身份不能混用。
     * operation 由调用场景明确传入，例如 MATERIAL_READ 或 START_CONTRACT；当前单项接口不携带模块/轮次/节点范围。
     * <p>返回 AccessDecision 和 projectVersion；allowed=false 是正常业务拒绝，HTTP/依赖失败不能当作允许访问。
     */
    @GetMapping("/internal/projects/{projectId}/access")
    ApiResponse<AccessDecision> decide(@RequestHeader("Authorization")String authorization,@PathVariable Long projectId,
            @RequestParam Long userId,@RequestParam(required=false)Long deptId,@RequestParam String operation,
            @RequestParam List<String> roles);

    /**
     * {@code POST /internal/projects/access-decisions}：批量判定一个 ActorContext 对多项目操作的权限。
     * <p>Authorization 使用内部 Bearer token；请求中的 actor 是业务身份，每个 target 可携带模块、轮次和节点范围。
     * <p>返回结果必须同时按 decisionId 与 projectId 关联原目标，不能只依赖列表位置；批量入口用于压缩工作台和任务鉴权 RPC。
     * 服务失败时调用方拒绝读取或办理，不使用缓存结果伪造授权成功。
     */
    @PostMapping("/internal/projects/access-decisions")
    ApiResponse<List<ProjectAccessResult>> decideBatch(@RequestHeader("Authorization") String authorization,
            @RequestBody ProjectAccessBatchRequest request);

    /**
     * {@code POST /internal/projects/{projectId}/expert-grants/sync}：为 Workflow 专家指派同步临时项目权限。
     * <p>Authorization 使用内部 Bearer token；路径 projectId 必须与 ExpertProjectGrantCommand.projectId 一致，
     * 授权范围由 expertUserId、moduleType、roundNo、taskNodeId 确定。
     * <p>同范围有效授权可幂等复用，返回 changed=false/ALREADY_ACTIVE；调用成功不代替 Workflow assignment 的创建。
     */
    @PostMapping("/internal/projects/{projectId}/expert-grants/sync")
    ApiResponse<Map<String, Object>> syncExpertGrant(@RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId, @RequestBody ExpertProjectGrantCommand command);

    /**
     * {@code POST /internal/projects/{projectId}/expert-grants/revoke}：回收指定 Workflow 指派范围的专家临时权限。
     * <p>Authorization 使用内部 Bearer token；命令范围必须与原指派一致，避免撤销专家在其他模块、轮次或节点的授权。
     * <p>返回 changed 与 REVOKED/NOT_ACTIVE；重复调用无活动授权时不报成功变更，个人评审历史不会随权限回收删除。
     */
    @PostMapping("/internal/projects/{projectId}/expert-grants/revoke")
    ApiResponse<Map<String, Object>> revokeExpertGrant(@RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId, @RequestBody ExpertProjectGrantCommand command);
}
