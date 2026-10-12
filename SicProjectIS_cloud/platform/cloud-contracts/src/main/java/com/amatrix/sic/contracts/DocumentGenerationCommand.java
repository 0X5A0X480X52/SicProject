package com.amatrix.sic.contracts;

import java.util.Map;

/**
 * Workflow 通过 Outbox 事件发送给 Material 的单据生成命令；它不是同步 Feign 请求。
 * @param commandId 生成幂等键，Material 用其抢占 generated_document；重复事件不会重复生成版本
 * @param projectId 输出材料所属项目
 * @param moduleInstanceId 触发生成的 Workflow 模块实例
 * @param stateRecordId 触发生成的状态历史记录 ID，不是状态序号
 * @param stateSeq 触发状态的模块序号，并用于生成文件名
 * @param moduleType APPLICATION、CONTRACT 或 ACCEPTANCE
 * @param nodeId 触发动作的 BPMN 节点代码
 * @param stateCode 迁移完成后的业务状态代码
 * @param documentTypeCode 生成单据类型代码
 * @param documentTypeName 单据展示名称
 * @param templateCode 渲染模板代码；当前渲染器按命令快照生成 PDF
 * @param snapshotSchemaJson 定义发布时固化的快照 schema 配置
 * @param snapshotViewName 定义发布时固化的快照视图名
 * @param outputMaterialTypeCode 输出 PDF 使用的材料类型代码，不能为空
 * @param outputMaterialTypeName 输出材料类型名称；空白时回退为类型代码
 * @param required 该单据在流程定义中的必需标志，随配置快照传递
 * @param actorUserId 触发迁移的业务用户，用作材料版本上传人
 * @param snapshot 迁移时固化的业务数据；消费者据此渲染，不回查并覆盖成后续业务状态
 */
public record DocumentGenerationCommand(
        String commandId,
        Long projectId,
        Long moduleInstanceId,
        Long stateRecordId,
        Integer stateSeq,
        String moduleType,
        String nodeId,
        String stateCode,
        String documentTypeCode,
        String documentTypeName,
        String templateCode,
        String snapshotSchemaJson,
        String snapshotViewName,
        String outputMaterialTypeCode,
        String outputMaterialTypeName,
        Boolean required,
        Long actorUserId,
        Map<String, Object> snapshot) {
}
