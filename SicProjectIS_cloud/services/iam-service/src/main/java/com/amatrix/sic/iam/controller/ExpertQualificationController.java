package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.ExpertQualificationService;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;

/**
 * 个人专家资格提交/查询及管理员资格列表、部门审核、科技处审核 API。
 */
@RestController
public class ExpertQualificationController {
    private final ExpertQualificationService service;

    public ExpertQualificationController(ExpertQualificationService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/expert-qualification/applications}：以当前账号提交专家资格申请，进入部门审核阶段。
     * <p>JSON 必填 specialty、applicationReason，可选 professionalTitle；申请人和部门由 IAM 当前账号记录确定。
     * <p>已具有 EXPERT 角色或已有活动申请时拒绝重复申请；返回新申请详情并写入资格提交事件。
     * <p>此接口只创建资格申请，专家角色在科技处审核通过后授予。
     */
    @PostMapping("/api/expert-qualification/applications")
    ApiResponse<Map<String, Object>> submit(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.submit(actor(jwt), request));
    }

    /**
     * {@code GET /api/expert-qualification/my}：查询本人的专家资格和全部资格申请历史。
     * <p>申请人由 JWT subject 确定，其他账号不能通过参数代查。
     * <p>返回 expert、hasActiveApplication、applications，供前端判断是否允许申请及展示审核进度。
     */
    @GetMapping("/api/expert-qualification/my")
    ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.mine(actor(jwt)));
    }

    /**
     * {@code GET /api/admin/expert-qualification/applications}：列出当前审核人员可处理的专家资格申请。
     * <p>要求 SYSTEM_ADMIN 或部门/科技处资格审核权限；纯部门审核人员仅查询所属部门范围。
     * <p>返回 applications 申请列表，包含状态与审核信息；此接口不改变申请状态。
     */
    @GetMapping("/api/admin/expert-qualification/applications")
    ApiResponse<Map<String, Object>> query(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.query(actor(jwt)));
    }

    /**
     * {@code POST /api/admin/expert-qualification/applications/{applicationId}/dept-review}：对指定专家资格申请执行部门审核。
     * <p>applicationId 为资格申请 ID；JSON 包含 approved、必填 opinion 和可选 remark。
     * <p>要求 SYSTEM_ADMIN 或 PERM_EXPERT_QUALIFICATION_REVIEW_DEPT；非系统管理员仅能审核本部门且待部门审核的申请。
     * <p>通过后进入 PENDING_SCIENCE_REVIEW，拒绝后为 DEPT_REJECTED；返回更新详情，竞争重复审核可返回 409。
     */
    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/dept-review")
    ApiResponse<Map<String, Object>> deptReview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.departmentReview(actor(jwt), applicationId, request));
    }

    /**
     * {@code POST /api/admin/expert-qualification/applications/{applicationId}/science-review}：执行专家资格的科技处终审，通过后授予 EXPERT 角色。
     * <p>applicationId 指定申请；JSON 包含 approved、必填 opinion 和可选 remark。
     * <p>要求 SYSTEM_ADMIN 或 PERM_EXPERT_QUALIFICATION_REVIEW_SCIENCE，申请必须处于 PENDING_SCIENCE_REVIEW。
     * <p>返回审核后的申请；通过时更新角色及权限版本并发送身份变更事件，申请人需重新登录取得新权限 token。
     */
    @PostMapping("/api/admin/expert-qualification/applications/{applicationId}/science-review")
    ApiResponse<Map<String, Object>> scienceReview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.scienceReview(actor(jwt), applicationId, request));
    }

    private static Long actor(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
