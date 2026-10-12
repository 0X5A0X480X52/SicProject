package com.amatrix.sic.workflow.controller.internal;

import com.amatrix.sic.workflow.service.saga.ApplicationSagaWorkflowService;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;

/**
 * 内部申请 Saga create/status/compensate HTTP 入口；Saga 不存在返回 404。
 */
@RestController
@RequestMapping("/internal/application-sagas")
public class InternalApplicationSagaController {
    private final ApplicationSagaWorkflowService service;
    public InternalApplicationSagaController(ApplicationSagaWorkflowService service){this.service=service;}
    /**
     * {@code POST /internal/application-sagas}：为 Project 申请 Saga 幂等创建 Workflow 侧的申请业务和模块。
     * <p>要求 SCOPE_internal；ApplicationSagaCommand 带 sagaId、项目、操作人及申请信息，sagaId 作为跨服务关联和幂等标识。
     * <p>返回 ApplicationSagaResult 的模块及初始状态；本地事务只覆盖 Workflow 数据，Project 仍需执行自己的确认阶段。
     */
    @PostMapping ApiResponse<ApplicationSagaResult> create(@RequestBody ApplicationSagaCommand command){return ApiResponse.ok(service.create(command));}
    /**
     * {@code GET /internal/application-sagas/{sagaId}}：查询申请 Saga 在 Workflow 侧的创建结果。
     * <p>要求 SCOPE_internal；sagaId 为跨服务 UUID，供 Project 在远程响应丢失或恢复时查证已创建结果。
     * <p>返回 ApplicationSagaResult；不存在返回 404，本接口不会新建流程或自动确认 Project 侧数据。
     */
    @GetMapping("/{sagaId}") ApiResponse<ApplicationSagaResult> status(@PathVariable UUID sagaId){ApplicationSagaResult result=service.find(sagaId);if(result==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application saga not found");return ApiResponse.ok(result);}
    /**
     * {@code DELETE /internal/application-sagas/{sagaId}}：补偿指定申请 Saga 在 Workflow 侧创建的业务数据。
     * <p>要求 SCOPE_internal；sagaId 标识需撤销的申请创建，服务仅清理该 Saga 关联的数据，不存在时直接返回。
     * <p>成功返回 data=null；只处理 Workflow 本地补偿，Project 本地状态和跨服务失败恢复由 Saga 编排服务继续处理。
     */
    @DeleteMapping("/{sagaId}") ApiResponse<Void> compensate(@PathVariable UUID sagaId){service.compensate(sagaId);return ApiResponse.ok(null);}
}
