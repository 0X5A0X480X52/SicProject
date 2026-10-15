package com.amatrix.sic.material.controller.internal;

import com.amatrix.sic.material.service.reservation.MaterialReservationService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

/**
 * 内部材料预留、确认、释放接口。
 */
@RestController
@RequestMapping("/internal/material-reservations")
public class InternalMaterialReservationController{
    private final MaterialReservationService service;public InternalMaterialReservationController(MaterialReservationService service){this.service=service;}
    /**
     * {@code POST /internal/material-reservations}：在流程正式迁移前，为本次提交预留所引用的材料版本。
     * <p>要求 SCOPE_internal；MaterialReservationCommand 包含 commandId、操作人、项目、模块、expectedSeq、版本 ID 及必需类型。
     * <p>服务校验版本归属、材料要求和可用状态后建立 15 分钟租约，返回预留结果；同 commandId 直接复用已有结果，不重新比对请求内容。
     * <p>不同竞争尝试必须用独立 commandId，Workflow 仍需在自己的迁移事务中复核 expectedSeq。
     * <p>同一 commandId 的重试必须保持请求内容一致，不能将旧幂等键用于另一组材料或另一次迁移。
     */
    @PostMapping ApiResponse<MaterialReservationResult> reserve(@RequestBody MaterialReservationCommand command){return ApiResponse.ok(service.reserve(command));}
    /**
     * {@code POST /internal/material-reservations/{id}/confirm}：将指定材料预留确认为已提交引用。
     * <p>要求 SCOPE_internal；id 为预留租约 ID，不是材料或版本 ID，通常在 Workflow 提交成功后调用或通过确认事件处理。
     * <p>支持重复确认；过期租约在材料仍可用时允许补确认，已释放租约不能据此复活。
     * <p>成功返回 data=null；本接口不推进 Workflow 状态。
     */
    @PostMapping("/{id}/confirm") ApiResponse<Void> confirm(@PathVariable String id){service.confirm(id);return ApiResponse.ok(null);}
    /**
     * {@code DELETE /internal/material-reservations/{id}}：释放失败或取消的流程提交所持有的材料预留。
     * <p>要求 SCOPE_internal；id 指定本次请求的独立预留租约，重复释放按服务状态规则处理。
     * <p>成功返回 data=null；只解除对应预留，不删除材料对象，也不能用失败请求释放其他竞争请求的租约。
     */
    @DeleteMapping("/{id}") ApiResponse<Void> release(@PathVariable String id){service.release(id);return ApiResponse.ok(null);}
}
