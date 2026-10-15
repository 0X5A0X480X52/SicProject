package com.amatrix.sic.contracts;

/**
 * 待判定项目操作及可选模块/轮次/任务节点范围。
 * @param decisionId 调用方生成的关联键，批量结果据此匹配原始目标
 * @param projectId 待检查的 Project 项目主键；与 decisionId 一起用于校验响应关联
 * @param operation 操作代码，如 VIEW_WORKFLOW、OPERATE_FINANCE_TASK 或 MATERIAL_READ；未知办理操作默认拒绝
 * @param moduleType 目标模块范围；null 时当前实现跳过该维度匹配，任务办理调用必须传入实际值
 * @param roundNo 当前业务轮次；null 时跳过轮次匹配，办理任务必须传入以阻止旧轮授权跨轮生效
 * @param taskNodeId BPMN 节点代码，不是 workflow_node_config 主键；null 时跳过节点维度匹配
 */
public record ProjectOperationTarget(
        String decisionId,
        Long projectId,
        String operation,
        String moduleType,
        Integer roundNo,
        String taskNodeId) {
}
