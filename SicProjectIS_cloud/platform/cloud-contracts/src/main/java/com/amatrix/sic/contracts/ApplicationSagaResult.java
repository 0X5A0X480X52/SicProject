package com.amatrix.sic.contracts;

import java.util.UUID;

/**
 * 申请 Saga 结果：Saga/项目/模块 ID、初始迁移及状态。
 * @param sagaId 原 ApplicationSagaCommand 的跨服务关联 UUID
 * @param projectId 对应 Project 项目主键
 * @param moduleInstanceId Workflow 创建的 APPLICATION 模块实例 ID
 * @param transition 初始状态记录、当前节点和 finished 标志
 * @param status Workflow 侧创建状态，当前成功值为 CREATED，不等于完整申请已审批
 */
public record ApplicationSagaResult(
        UUID sagaId,
        Long projectId,
        Long moduleInstanceId,
        StateTransitionContract transition,
        String status) {
}
