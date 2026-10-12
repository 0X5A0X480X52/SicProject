package com.amatrix.sic.contracts;

/**
 * Workflow 通过 Feign 同步或撤销专家临时项目授权的命令。
 * @param projectId 目标 Project 项目主键，必须与 Feign 路径参数一致
 * @param expertUserId 被指派专家的 IAM 用户 ID，Project 同步时复核 EXPERT 角色
 * @param expertName 指派时保存的专家姓名；撤销时可为空
 * @param moduleType 授权所属 APPLICATION、CONTRACT 或 ACCEPTANCE 模块
 * @param roundNo 当前评审批次对应的模块业务轮次，阻止旧轮授权跨轮生效
 * @param taskNodeId 专家评审 BPMN 节点代码，不是 workflow_node_config 主键
 * @param grantedByUserId 发起指派或撤销的操作人 ID，用于 Project 审计和事件
 * @param grantedByName 操作人展示名，用于内部 ActorContext 和审计
 */
public record ExpertProjectGrantCommand(
        Long projectId,
        Long expertUserId,
        String expertName,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        Long grantedByUserId,
        String grantedByName) {
}
