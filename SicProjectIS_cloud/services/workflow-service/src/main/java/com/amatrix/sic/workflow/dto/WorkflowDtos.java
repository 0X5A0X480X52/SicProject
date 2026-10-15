package com.amatrix.sic.workflow.dto;

import java.util.List;
import java.util.Map;

/** 工作流定义上传、模块启动及迁移的请求载体；迁移序号和具体材料版本分别用于并发校验与引用。 */
public final class WorkflowDtos {
    private WorkflowDtos(){}

    /**
     * 工作流定义上传请求。
     * @param bpmnXml 完整 BPMN XML；服务解析并校验后保存为 DRAFT，不因上传自动发布
     */
    public record UploadWorkflowDefinitionRequest(String bpmnXml){}

    /**
     * 为已有项目启动后继模块的请求。
     * @param moduleType 仅支持 CONTRACT 或 ACCEPTANCE；APPLICATION 必须通过项目申请 Saga 启动
     */
    public record StartModuleRequest(String moduleType){}
    /**
     * 正式节点迁移的输入，表单数据随迁移事务一起保存。
     * @param eventType 必填迁移事件，必须与当前 BPMN 节点的一条可用连线匹配
     * @param expectedSeq 客户端观察到的模块序号；兼容省略时使用预读序号，锁内仍再次校验
     * @param result 可选业务结果，也可参与 BPMN 条件判断
     * @param remark 可选迁移意见或说明，写入状态历史
     * @param materialVersionIds 具体材料版本 ID，用于独立预留及提交后的状态引用
     * @param formCode 注册表单代码；旧定义的节点键兼容由表单服务处理
     * @param nodeFormData 当前节点表单数据；服务保护项目、模块等上下文字段并按 dataKind 落入业务表
     */
    public record TransitionRequest(String eventType,Integer expectedSeq,String result,String remark,
            List<Long> materialVersionIds,String formCode,Map<String,Object> nodeFormData){}
}
