package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.business.StructuredBusinessService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

/**
 * 项目下 applications/contracts/acceptances 读写、achievements 和 acceptance-financial-settlements 查询/新增 API。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class StructuredBusinessController {
    private final StructuredBusinessService service;

    public StructuredBusinessController(StructuredBusinessService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/projects/{projectId}/applications}：读取项目申请正文及扩展信息。
     * <p>projectId 为项目主键，要求项目读取权限；返回 application、extension、detail 等保存结构。
     * <p>没有申请数据时返回空值结构，兼容旧数据的非结构化存储；查询本身不创建申请或模块。
     */
    @GetMapping("/applications")
    ApiResponse<Map<String, Object>> application(@PathVariable Long projectId) {
        return ApiResponse.ok(service.application(projectId));
    }

    /**
     * {@code PUT /api/projects/{projectId}/applications}：更新当前申请填报节点的申请正文。
     * <p>JSON 必须含 application 对象，服务定位 APPLICATION 的 SubmitApplicationTask / APPLICATION_DRAFT 并检查当前操作人。
     * <p>更新申请标题、限额标志、摘要与完整 JSON，返回可读回的申请结构。
     * <p>只保存当前业务正文，不推进节点；已离开填报节点时不能通过此接口改写。
     */
    @PutMapping("/applications")
    ApiResponse<Map<String, Object>> saveApplication(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveApplication(projectId, ActorContextFactory.from(jwt), request));
    }

    /**
     * {@code GET /api/projects/{projectId}/contracts}：读取项目合同业务草稿。
     * <p>要求项目读取权限；projectId 定位项目 CONTRACT 草稿，返回 contract、extension 等结构。
     * <p>尚未保存时返回空草稿结构，本接口不启动合同模块或生成 PDF。
     */
    @GetMapping("/contracts")
    ApiResponse<Map<String, Object>> contract(@PathVariable Long projectId) {
        return ApiResponse.ok(service.contract(projectId));
    }

    /**
     * {@code PUT /api/projects/{projectId}/contracts}：保存当前合同填报节点的合同草稿。
     * <p>JSON 必须含 contract 对象；服务限定 CONTRACT 的 FillContractTask / CONTRACT_DRAFT 并检查当前操作资格。
     * <p>按当前模块和轮次保存，返回合同草稿；保存不代表合同已经提交审批或流程已推进。
     */
    @PutMapping("/contracts")
    ApiResponse<Map<String, Object>> saveContract(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveContract(projectId, ActorContextFactory.from(jwt), request));
    }

    /**
     * {@code GET /api/projects/{projectId}/acceptances}：读取项目验收申请业务草稿。
     * <p>要求项目读取权限；projectId 定位项目 ACCEPTANCE 草稿，返回 acceptance、extension 等结构。
     * <p>尚未保存时返回空结构，不在读取时创建验收流程或验收终态。
     */
    @GetMapping("/acceptances")
    ApiResponse<Map<String, Object>> acceptance(@PathVariable Long projectId) {
        return ApiResponse.ok(service.acceptance(projectId));
    }

    /**
     * {@code PUT /api/projects/{projectId}/acceptances}：保存当前验收材料填报节点的验收业务草稿。
     * <p>JSON 必须含 acceptance 对象；服务限定 ACCEPTANCE 的 SubmitAcceptanceMaterialsTask / ACCEPTANCE_MATERIAL_DRAFT。
     * <p>复核当前操作人后按模块/轮次保存并返回验收草稿；正式材料提交和流程推进由 transitions 完成。
     */
    @PutMapping("/acceptances")
    ApiResponse<Map<String, Object>> saveAcceptance(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveAcceptance(projectId, ActorContextFactory.from(jwt), request));
    }

    /**
     * {@code GET /api/projects/{projectId}/achievements}：列出项目登记的科研成果记录。
     * <p>要求项目读取权限；返回成果类型、标题、作者、日期及材料证明等成果数据。
     * <p>本接口读取项目成果集合，不上传成果证明文件或新增成果。
     */
    @GetMapping("/achievements")
    ApiResponse<List<Map<String, Object>>> achievements(@PathVariable Long projectId) {
        return ApiResponse.ok(service.achievements(projectId));
    }

    /**
     * {@code POST /api/projects/{projectId}/achievements}：在验收材料填报阶段新增一项项目成果。
     * <p>要求当前 SubmitAcceptanceMaterialsTask 操作资格；JSON 必填 achievementType、achievementTitle，可提供作者、日期和 proofMaterialVersionId。
     * <p>moduleInstanceId、acceptanceId 若提供必须属于当前模块；proofMaterialVersionId 是已上传材料版本 ID。
     * <p>返回新增成果记录，不推进验收流程，也不在此上传证明文件。
     */
    @PostMapping("/achievements")
    ApiResponse<Map<String, Object>> addAchievement(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.addAchievement(projectId, ActorContextFactory.from(jwt), request));
    }

    /**
     * {@code GET /api/projects/{projectId}/acceptance-financial-settlements}：查询项目验收财务结算记录。
     * <p>要求项目读取权限；返回结算记录列表，包括到账、支出、结余、执行率和财务意见等字段。
     * <p>读取历史结算不意味着可以办理当前财务节点，办理资格单独按角色和项目授权判断。
     */
    @GetMapping("/acceptance-financial-settlements")
    ApiResponse<List<Map<String, Object>>> settlements(@PathVariable Long projectId) {
        return ApiResponse.ok(service.settlements(projectId));
    }

    /**
     * {@code POST /api/projects/{projectId}/acceptance-financial-settlements}：在当前验收财务节点新增结算记录。
     * <p>要求 ACCEPTANCE 的 FinancialSettlementTask / ACCEPTANCE_FINANCIAL_SETTLEMENT 当前操作资格，财务角色与授权范围均需匹配。
     * <p>JSON receivedAmount、spentAmount 必填且非负；可提供 moduleInstanceId、acceptanceId、stateRecordId，均按当前模块/轮次校验。
     * <p>计算结余=到账−支出、执行率=支出/到账×100（到账为零时为零），返回新增记录；结算登记不自动推进节点。
     */
    @PostMapping("/acceptance-financial-settlements")
    ApiResponse<Map<String, Object>> addSettlement(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.addSettlement(projectId, ActorContextFactory.from(jwt), request));
    }
}
