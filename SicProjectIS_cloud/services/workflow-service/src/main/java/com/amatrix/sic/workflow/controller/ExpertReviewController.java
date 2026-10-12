package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.review.ExpertReviewService;
import com.amatrix.sic.workflow.dto.ExpertReviewDtos;

import static com.amatrix.sic.workflow.dto.ExpertReviewDtos.*;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

/**
 * 专家评审批次创建/详情、专家分配、评分提交和分配删除 API；保留 POST remove 兼容入口。
 */
@RestController
@RequestMapping("/api/expert-reviews")
public class ExpertReviewController {
    private final ExpertReviewService service;
    public ExpertReviewController(ExpertReviewService service) { this.service = service; }

    /**
     * {@code POST /api/expert-reviews/batches}：在当前专家指派节点建立本轮评审批次。
     * <p>JSON CreateBatchRequest 指定 moduleInstanceId、workflowNodeId、reviewType、标题及评分规则；workflowNodeId 是节点配置主键。
     * <p>服务校验当前节点和操作人，按模块、节点与轮次处理批次，返回批次详情。
     * <p>创建批次不等于完成指派；expectedExpertCount 和 minExpertCount 为规则配置，完成判断以实际已指派/已提交统计为准。
     */
    @PostMapping("/batches")
    ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateBatchRequest request) {
        return ApiResponse.ok(service.create(ActorContextFactory.from(jwt), request));
    }
    /**
     * {@code GET /api/expert-reviews/batches/{batchId}}：读取评审批次、指派进度及评分汇总。
     * <p>batchId 为批次主键；服务检查操作人的批次访问资格并按身份限制返回内容。
     * <p>管理员/业务操作人与被指派专家可见内容不同，专家的个人任务及历史优先使用 my-assignments 接口。
     */
    @GetMapping("/batches/{batchId}")
    ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.ok(service.detail(ActorContextFactory.from(jwt), batchId));
    }
    /**
     * {@code POST /api/expert-reviews/batches/{batchId}/assignments}：向当前批次指派一名专家，并同步其项目临时授权。
     * <p>batchId 指定批次；JSON expertUserId 指定专家，可提供 expertName、expertOrg、expertTitle 展示资料。
     * <p>服务复核当前指派节点、轮次及操作资格，查询专家身份，创建 assignment 后同步对应模块/轮次/节点项目权限。
     * <p>返回更新后的批次详情；这是正式任务指派，单独创建 Project 授权记录不能代替此接口。
     */
    @PostMapping("/batches/{batchId}/assignments")
    ApiResponse<Map<String,Object>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId,
            @RequestBody AssignExpertRequest request) {
        return ApiResponse.ok(service.assign(ActorContextFactory.from(jwt), batchId, request));
    }
    /**
     * {@code POST /api/expert-reviews/assignments/{assignmentId}/scores}：提交当前登录专家对本人 assignment 的分项评分及评审意见。
     * <p>JSON SubmitScoreRequest 包含 conflictOfInterest、valid、reviewResult、reviewComment 和 scores；assignmentId 不同于 batchId。
     * <p>服务按模块→批次→assignment 加锁，复核当前轮次及本人任务权限；已提交不能再次覆盖，过期轮次不能继续评分。
     * <p>总分按 scoreValue×weight 汇总并保留两位小数；返回按当前身份裁剪的评审结果。
     * <p>个人提交先改变 assignment；全部已指派人员提交后才完成批次并发出推进事件，项目授权回收也有独立处理阶段。
     */
    @PostMapping("/assignments/{assignmentId}/scores")
    ApiResponse<Map<String,Object>> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId,
            @RequestBody SubmitScoreRequest request) {
        return ApiResponse.ok(service.submit(ActorContextFactory.from(jwt), assignmentId, request));
    }
    /**
     * {@code DELETE /api/expert-reviews/assignments/{assignmentId}}：移除评审批次中的一个专家指派，并回收对应临时授权。
     * <p>assignmentId 为个人指派记录 ID；服务要求当前专家指派节点的操作资格，并检查批次及个人提交状态。
     * <p>已提交或不属于可修改阶段的任务不能通过移除抹除评分；成功返回更新后的批次详情。
     * <p>回收的是该模块、轮次、节点范围的专家权限，不删除账号或其他评审历史。
     */
    @DeleteMapping("/assignments/{assignmentId}")
    ApiResponse<Map<String,Object>> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId) {
        return ApiResponse.ok(service.remove(ActorContextFactory.from(jwt), assignmentId));
    }
    /**
     * {@code POST /api/expert-reviews/assignments/{assignmentId}/remove}：为兼容调用方提供 POST 形式的专家指派移除入口。
     * <p>行为、权限、状态限制和返回值与 DELETE /api/expert-reviews/assignments/{assignmentId} 完全一致。
     * <p>直接委托同一个移除方法，不是额外的强制删除或绕过评分保护接口。
     */
    @PostMapping("/assignments/{assignmentId}/remove")
    ApiResponse<Map<String,Object>> removeFallback(@AuthenticationPrincipal Jwt jwt, @PathVariable Long assignmentId) {
        return remove(jwt, assignmentId);
    }
}
