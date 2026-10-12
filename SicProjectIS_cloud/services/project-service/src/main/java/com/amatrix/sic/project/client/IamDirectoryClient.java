package com.amatrix.sic.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.IamOverviewStats;
import com.amatrix.sic.contracts.UserSummary;

/**
 * Feign 调用 IAM 内部用户摘要及管理员概览统计。
 */
@FeignClient(name = "iam-service", contextId = "projectIamDirectoryClient")
public interface IamDirectoryClient {
    /**
     * {@code GET /internal/departments/{deptId}/summary}：读取部门名称和启用状态。
     * <p>Authorization 必须是内部 Bearer token；用于项目申请和历史空部门修复，不接受用户 token 代替服务身份。
     * <p>返回 DepartmentSummary；部门不存在为 404，调用方仍须检查 enabled，不能仅因查询成功就允许使用该部门。
     */
    @GetMapping("/internal/departments/{deptId}/summary")
    ApiResponse<com.amatrix.sic.contracts.DepartmentSummary> department(
            @RequestHeader("Authorization") String authorization, @PathVariable Long deptId);

    /**
     * {@code POST /internal/users/summaries}：批量读取 IAM 当前用户摘要。
     * <p>Authorization 使用内部 Bearer token；请求体中的用户 ID 去重后最多 100 个，IAM 会拒绝空值、非正数和超限集合。
     * <p>返回结果只包含存在的用户，调用方必须按 userId 关联并检查完整性，不能依赖输入顺序或相同长度。
     * 此接口用于减少逐用户 RPC，不返回密码、联系方式或权限代码明细。
     */
    @org.springframework.web.bind.annotation.PostMapping("/internal/users/summaries")
    ApiResponse<java.util.List<UserSummary>> summaries(@RequestHeader("Authorization") String authorization,
            @org.springframework.web.bind.annotation.RequestBody java.util.List<Long> userIds);

    /**
     * {@code GET /internal/users/{userId}/summary}：读取一个用户的当前身份摘要。
     * <p>使用内部 Bearer token；返回部门、角色、启用状态和 authVersion，不返回密码。
     * 用户不存在时为 404；Project 可用结果补齐或刷新本地 user_snapshot，但已有快照不会因每次读取自动刷新。
     */
    @GetMapping("/internal/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@RequestHeader("Authorization") String authorization, @PathVariable Long userId);

    /**
     * {@code GET /internal/admin/overview-stats}：读取 IAM 用户、角色和权限汇总。
     * <p>使用内部 Bearer token；返回 IamOverviewStats，供 Project 管理概览与本地授权、审计统计组合。
     * 这是聚合查询，不返回用户清单，也不会在 IAM 不可用时由 Feign 伪造零值。
     */
    @GetMapping("/internal/admin/overview-stats")
    ApiResponse<IamOverviewStats> overviewStats(@RequestHeader("Authorization") String authorization);
}
