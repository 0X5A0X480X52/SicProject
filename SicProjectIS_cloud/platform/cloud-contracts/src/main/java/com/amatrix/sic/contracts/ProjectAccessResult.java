package com.amatrix.sic.contracts;

/**
 * 单个批量判定结果，附 decisionId、允许/原因、版本、负责人及部门上下文。
 * @param decisionId 原 ProjectOperationTarget 的调用方关联键
 * @param projectId 原目标项目 ID；调用方应与 decisionId 一起复核，防止错配结果
 * @param allowed 该目标操作是否获准
 * @param projectVersion 判定时项目授权版本，项目不存在时为 0
 * @param reasonCode ALLOWED 或拒绝原因代码
 * @param ownerUserId 当前项目负责人 ID，项目不可用时可为空
 * @param ownerName 当前项目负责人姓名快照
 * @param deptId 当前项目所属部门 ID
 */
public record ProjectAccessResult(
        String decisionId,
        Long projectId,
        boolean allowed,
        long projectVersion,
        String reasonCode,
        Long ownerUserId,
        String ownerName,
        Long deptId) {
}
