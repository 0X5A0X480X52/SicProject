package com.amatrix.sic.workflow.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

/**
 * Feign 调 Material 预留及释放接口；成功确认通过事件完成。
 */
@FeignClient(name="material-service",contextId="workflowMaterialReservationClient")
public interface MaterialReservationClient {
    /**
     * {@code POST /internal/material-reservations}：在流程迁移前预留本次引用的材料版本。
     * <p>Authorization 使用 workflow-service 内部 Bearer token；commandId 是本次尝试的幂等键，
     * projectId、moduleInstanceId、expectedSeq 和 materialVersionIds 共同描述待提交上下文。
     * <p>返回租约 ID、状态、过期时间及版本类型映射；预留成功不代表流程已提交，Workflow 仍需锁内复核 expectedSeq。
     * Feign 调用失败不会伪造成功，调用方只释放自己已取得的 reservationId。
     */
    @PostMapping("/internal/material-reservations")
    ApiResponse<MaterialReservationResult> reserve(@RequestHeader("Authorization")String authorization,
            @RequestBody MaterialReservationCommand command);

    /**
     * {@code DELETE /internal/material-reservations/{reservationId}}：释放失败迁移占用的材料租约。
     * <p>Authorization 使用内部 Bearer token；reservationId 是 reserve 返回的租约 ID，不是 materialVersionId。
     * <p>接收端只将仍为 RESERVED 的租约释放，不撤销已 CONFIRMED 的材料引用，也不删除材料对象。
     * 成功响应 data 为 null；重复释放不会推进或回滚 Workflow。
     */
    @DeleteMapping("/internal/material-reservations/{reservationId}")
    ApiResponse<Void> release(@RequestHeader("Authorization")String authorization,@PathVariable String reservationId);
}
