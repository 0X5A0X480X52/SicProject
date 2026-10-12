package com.amatrix.sic.workflow.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.service.review.ExpertPersonalReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/expert-reviews/my-assignments 及其详情入口，从 JWT 解析操作人并委托本人评审服务。
 */
@RestController
@RequestMapping("/api/expert-reviews")
public class ExpertPersonalReviewController {
    private final ExpertPersonalReviewService service;
    public ExpertPersonalReviewController(ExpertPersonalReviewService service) {this.service=service;}
    /**
     * {@code GET /api/expert-reviews/my-assignments}：分页查询本人的待评审任务和已提交、已完成等历史指派。
     * <p>当前账号由 JWT 确定；status 可选 PENDING、WAITING_REVIEW、SUBMITTED、COMPLETED、EXPIRED。
     * <p>page 缺省 1，pageSize 缺省 10，范围分别为 1..1000、1..100；返回 items、page、pageSize、hasMore，并标注 canReview。
     * <p>历史查询仅要求登录，不依赖仍持有 EXPERT 角色或项目临时授权；能否评分另按当前节点权限判断。
     */
    @GetMapping("/my-assignments")
    ApiResponse<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.list(ActorContextFactory.from(jwt),status,page,pageSize));
    }
    /**
     * {@code GET /api/expert-reviews/my-assignments/{assignmentId}}：读取本人某次专家指派的详情和本人评分明细。
     * <p>assignmentId 是个人指派 ID；不存在或属于其他账号均返回 404，避免泄露其他专家任务。
     * <p>返回指派信息、scores、canReview；历史记录可读不代表可继续评分或读取完整项目材料。
     * <p>后续评分使用 scores 提交入口，项目/材料读取仍由各自权限接口校验。
     */
    @GetMapping("/my-assignments/{assignmentId}")
    ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable Long assignmentId) {
        return ApiResponse.ok(service.detail(ActorContextFactory.from(jwt),assignmentId));
    }
}
