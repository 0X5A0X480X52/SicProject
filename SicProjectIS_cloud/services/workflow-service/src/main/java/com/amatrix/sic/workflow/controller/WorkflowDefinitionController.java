package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.definition.WorkflowDefinitionService;
import com.amatrix.sic.workflow.dto.WorkflowDtos;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.workflow.dto.WorkflowDtos.UploadWorkflowDefinitionRequest;

/**
 * 流程定义上传、资产列表/上传/发布、验证、发布、最新版本、详情、BPMN、节点及连线 API。
 */
@RestController
@RequestMapping("/api/workflow-definitions")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/workflow-definitions/upload}：上传 BPMN XML，建立新的工作流定义草稿及解析配置。
     * <p>要求 PERM_WORKFLOW_DEFINITION_UPLOAD；JSON bpmnXml 为完整流程定义，先解析并校验节点、连线和业务字段。
     * <p>返回定义摘要，初始状态 DRAFT；同模块及版本重复返回 409，上传成功不代表已经发布供新流程使用。
     */
    @PostMapping("/upload")
    ApiResponse<Map<String, Object>> upload(@RequestBody UploadWorkflowDefinitionRequest request) {
        return ApiResponse.ok(service.upload(request == null ? null : request.bpmnXml()));
    }

    /**
     * {@code GET /api/workflow-definitions/assets}：列出服务内置的 BPMN 资产及其模块和版本信息。
     * <p>要求 PERM_WORKFLOW_DEFINITION_VIEW；返回 assetName、resourcePath、processKey、moduleType、versionNo。
     * <p>这是可导入资源目录，与数据库中已上传/发布的定义列表不同。
     */
    @GetMapping("/assets")
    ApiResponse<List<Map<String, Object>>> assets() {
        return ApiResponse.ok(service.listAssets());
    }

    /**
     * {@code POST /api/workflow-definitions/assets/{assetName}/upload}：把指定内置 BPMN 资产导入为定义草稿。
     * <p>要求 PERM_WORKFLOW_DEFINITION_UPLOAD；assetName 必须属于内置资产目录，不接受任意文件系统路径。
     * <p>读取内置 XML 后复用上传校验和版本唯一约束，返回草稿摘要；不会自动发布。
     */
    @PostMapping("/assets/{assetName}/upload")
    ApiResponse<Map<String, Object>> uploadAsset(@PathVariable String assetName) {
        return ApiResponse.ok(service.uploadAsset(assetName));
    }

    /**
     * {@code POST /api/workflow-definitions/assets/{assetName}/publish}：导入并发布指定内置资产对应的定义版本。
     * <p>要求 PERM_WORKFLOW_DEFINITION_PUBLISH；assetName 限定为内置目录名称，无须提供 XML 请求体。
     * <p>已有同模块/版本定义时复用该定义，不存在时先导入；发布会停用同模块旧发布版本并返回定义详情。
     * <p>这不会把已有模块实例自动迁移到新的定义。
     */
    @PostMapping("/assets/{assetName}/publish")
    ApiResponse<Map<String, Object>> publishAsset(@PathVariable String assetName) {
        return ApiResponse.ok(service.publishAsset(assetName));
    }

    /**
     * {@code POST /api/workflow-definitions/{id}/validate}：检查已保存定义的 BPMN 配置是否满足运行约束。
     * <p>要求 PERM_WORKFLOW_DEFINITION_VALIDATE；id 为 workflowDefinitionId，返回校验结果、错误及警告信息。
     * <p>本接口不发布定义、不推进运行实例；不存在的定义返回 404。
     */
    @PostMapping("/{id}/validate")
    ApiResponse<Map<String, Object>> validate(@PathVariable Long id) {
        return ApiResponse.ok(service.validate(id));
    }

    /**
     * {@code POST /api/workflow-definitions/{id}/publish}：发布已保存的工作流定义，作为对应模块的有效定义。
     * <p>要求 PERM_WORKFLOW_DEFINITION_PUBLISH；id 是定义主键，发布前重新解析校验，失败不激活定义。
     * <p>同模块旧发布版本被停用，节点配置在本地事务中持久化；返回 definition、validation、nodes、transitions。
     * <p>已有实例继续关联原定义，不能把发布理解为运行中实例的版本迁移。
     */
    @PostMapping("/{id}/publish")
    ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    /**
     * {@code GET /api/workflow-definitions}：查询数据库中的全部工作流定义摘要。
     * <p>要求 PERM_WORKFLOW_DEFINITION_VIEW；返回定义版本、状态及发布标志等摘要，涵盖草稿和历史定义。
     * <p>与 assets 的内置资源目录不同，也不是当前账号可见项目的流程列表。
     */
    @GetMapping
    ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * {@code GET /api/workflow-definitions/latest}：按模块类型读取最新可用的已发布工作流定义。
     * <p>要求 PERM_WORKFLOW_DEFINITION_VIEW；查询参数 moduleType 必填，用于申请、合同或验收模块选择。
     * <p>返回定义摘要，没有匹配定义时返回 404；本接口不启动模块实例。
     */
    @GetMapping("/latest")
    ApiResponse<Map<String, Object>> latest(@RequestParam String moduleType) {
        return ApiResponse.ok(service.latest(moduleType));
    }

    /**
     * {@code GET /api/workflow-definitions/{id}}：读取流程定义的管理详情及解析后的配置。
     * <p>要求 PERM_WORKFLOW_DEFINITION_VIEW；id 指定定义主键，返回 definition、validation、nodes、transitions。
     * <p>这是定义管理接口；普通业务人员读取关联流程图时使用 bpmn、nodes、transitions 的项目范围入口。
     */
    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /**
     * {@code GET /api/workflow-definitions/{id}/bpmn}：读取某个定义的原始 BPMN XML，供流程图渲染。
     * <p>要求定义查看权限，或能够读取关联该定义的项目流程；id 是定义主键，不是模块实例 ID。
     * <p>返回 workflowDefinitionId、bpmnXml 的 ApiResponse JSON，不直接输出 XML 文件。
     * <p>项目范围的读取许可不等于获得全局定义列表或发布权限。
     */
    @GetMapping("/{id}/bpmn")
    ApiResponse<Map<String, Object>> bpmn(@PathVariable Long id) {
        return ApiResponse.ok(service.bpmn(id));
    }

    /**
     * {@code GET /api/workflow-definitions/{id}/nodes}：读取流程定义的节点配置，供前端标识责任人、表单与材料要求。
     * <p>要求定义查看权限或关联项目的流程读取资格；id 为定义主键。
     * <p>返回 nodeId、状态、责任角色、operationMode、材料要求和单据配置等节点列表，不是实际待办实例列表。
     */
    @GetMapping("/{id}/nodes")
    ApiResponse<List<Map<String, Object>>> nodes(@PathVariable Long id) {
        return ApiResponse.ok(service.nodes(id));
    }

    /**
     * {@code GET /api/workflow-definitions/{id}/transitions}：读取流程定义的连线及迁移规则。
     * <p>要求定义查看权限或关联项目流程读取资格；id 为定义主键，返回解析后的迁移配置列表。
     * <p>这表示定义中允许的路径，实际提交仍需 runtime-view 的当前节点、权限及 expectedSeq 校验。
     */
    @GetMapping("/{id}/transitions")
    ApiResponse<List<Map<String, Object>>> transitions(@PathVariable Long id) {
        return ApiResponse.ok(service.transitions(id));
    }
}
