package com.amatrix.sic.project.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;

/**
 * Feign 调用 Workflow 内部申请 Saga 创建、状态查询、补偿删除；创建时转发用户授权 Header。
 */
@FeignClient(name = "workflow-service", contextId = "projectWorkflowSagaClient")
public interface WorkflowSagaClient {
    /**
     * {@code POST /internal/application-sagas}：在 Workflow 创建或复用申请模块。
     * <p>Authorization 使用 project-service 的内部 Bearer token，接收端以 {@code SCOPE_internal} 鉴权；
     * X-User-Authorization 转发原用户 token，但当前 Workflow Controller 不读取该 Header，不能把它解释为远端用户鉴权。
     * <p>command 的 sagaId 是幂等关联键，项目和申请数据来自 Project 已创建的临时记录。
     * 返回 Workflow 侧的 moduleInstanceId、初始迁移和 CREATED 状态；HTTP 或业务失败由 Saga 编排层执行补偿。
     */
    @PostMapping("/internal/application-sagas")
    ApiResponse<ApplicationSagaResult> create(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestHeader("X-User-Authorization") String userAuthorization,
            @RequestBody ApplicationSagaCommand command);

    /**
     * {@code GET /internal/application-sagas/{sagaId}}：查询 Workflow 侧已创建的申请 Saga 结果。
     * <p>使用内部 Bearer token；sagaId 是 Project 与 Workflow 共用的 UUID，不是项目 ID或模块 ID。
     * <p>用于远程响应丢失后的查证和 WORKFLOW_CREATED 确认恢复；不存在时接收端返回 404，查询不会重新创建流程。
     */
    @GetMapping("/internal/application-sagas/{sagaId}")
    ApiResponse<ApplicationSagaResult> status(@RequestHeader("Authorization") String serviceAuthorization,
            @PathVariable UUID sagaId);

    /**
     * {@code DELETE /internal/application-sagas/{sagaId}}：请求 Workflow 补偿该 Saga 创建的数据。
     * <p>使用内部 Bearer token；删除范围由 sagaId 限定，重复补偿无对应模块时仍可安全返回。
     * <p>成功只表示 Workflow 本地补偿完成；Project 临时项目和 Saga 状态由调用方在自己的事务中继续处理。
     */
    @DeleteMapping("/internal/application-sagas/{sagaId}")
    ApiResponse<Void> compensate(@RequestHeader("Authorization") String serviceAuthorization,
            @PathVariable UUID sagaId);
}
