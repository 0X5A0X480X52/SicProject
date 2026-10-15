package com.amatrix.sic.contracts;

import java.util.List;

/**
 * 材料预留命令：commandId、操作人、项目、模块、expectedSeq、版本列表、必需材料类型。
 * @param commandId 本次预留的幂等键；不同迁移竞争应使用不同键，避免释放他人的租约
 * @param actorUserId 发起迁移的业务用户 ID，记录在预留中；服务身份由 Authorization Header 单独提供
 * @param projectId 材料必须归属的项目主键
 * @param moduleInstanceId 发起提交的 Workflow 模块实例 ID
 * @param expectedSeq 预留时观察到的模块序号，Workflow 迁移事务中还需再次校验
 * @param materialVersionIds 具体版本 ID，而非材料主表 ID，提交后关联到状态记录
 * @param requiredMaterialTypeCodes 当前 BPMN 节点在提交前要求的材料类型；每类至少需有一个所选版本
 */
public record MaterialReservationCommand(
        String commandId,
        Long actorUserId,
        Long projectId,
        Long moduleInstanceId,
        Integer expectedSeq,
        List<Long> materialVersionIds,
        List<String> requiredMaterialTypeCodes) {
}
