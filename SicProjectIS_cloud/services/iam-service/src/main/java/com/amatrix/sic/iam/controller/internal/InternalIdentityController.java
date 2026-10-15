package com.amatrix.sic.iam.controller.internal;

import com.amatrix.sic.iam.service.IamOverviewService;
import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.config.properties.ServiceTokenProperties;
import com.amatrix.sic.iam.security.JwtTokenIssuer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.IamOverviewStats;
import com.amatrix.sic.contracts.UserSummary;

/**
 * 内部服务 Token 兑换（恒定时间比较 secret）、用户摘要和管理员概览统计接口。
 */
@RestController
@RequestMapping("/internal")
public class InternalIdentityController {
    private final IamRepository repository;
    private final JwtTokenIssuer tokenIssuer;
    private final ServiceTokenProperties tokenProperties;
    private final IamOverviewService overview;
    private final com.amatrix.sic.iam.dao.AdminMapper departments;

    public InternalIdentityController(IamRepository repository, JwtTokenIssuer tokenIssuer,
            ServiceTokenProperties tokenProperties, IamOverviewService overview,
            com.amatrix.sic.iam.dao.AdminMapper departments) {
        this.repository = repository;
        this.tokenIssuer = tokenIssuer;
        this.tokenProperties = tokenProperties;
        this.overview = overview;
        this.departments = departments;
    }

    /**
     * {@code POST /internal/tokens/service}：使用服务引导凭据兑换内部调用 JWT。
     * <p>该兑换入口不要求已有用户 JWT；必须在 X-Service-Bootstrap-Secret 请求头提供匹配的引导 secret，查询参数 serviceName 标识服务。
     * <p>secret 使用恒定时间比较，不匹配返回 401；返回 IssuedToken，包含 internal scope，供后续内部接口调用。
     * <p>这是服务凭据接口，不用于前端用户登录；当前实现未在此校验 serviceName 白名单。
     */
    @PostMapping("/tokens/service")
    ApiResponse<JwtTokenIssuer.IssuedToken> serviceToken(
            @RequestHeader("X-Service-Bootstrap-Secret") String secret,
            @RequestParam String serviceName) {
        if (tokenProperties.bootstrapSecret() == null || !MessageDigest.isEqual(
                tokenProperties.bootstrapSecret().getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
        return ApiResponse.ok(tokenIssuer.issueServiceToken(serviceName, List.of("internal")));
    }

    /**
     * {@code GET /internal/users/{userId}/summary}：向其他服务提供单个用户的最小身份摘要。
     * <p>要求内部服务 token 的 SCOPE_internal；userId 为 IAM 用户 ID。
     * <p>返回 UserSummary，包含部门、启用状态、角色及权限版本等协作字段，不包含密码；用户不存在返回 404。
     */
    @GetMapping("/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        var users = repository.summaries(List.of(userId));
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return ApiResponse.ok(users.getFirst());
    }

    /**
     * {@code POST /internal/users/summaries}：一次查询多个用户的身份摘要，减少逐用户远程请求。
     * <p>要求 SCOPE_internal；JSON 请求体为用户 ID 数组，服务去重并只返回存在的用户。
     * <p>最多查询 100 个去重后的正整数 ID，超出范围或存在非法 ID 时返回 400；空集合返回空列表。
     * <p>返回 UserSummary 列表，缺失用户不补占位项；调用方应按 userId 关联结果，不能依赖输入顺序或相同长度。
     */
    @PostMapping("/users/summaries")
    ApiResponse<List<UserSummary>> summaries(@org.springframework.web.bind.annotation.RequestBody List<Long> userIds) {
        return ApiResponse.ok(repository.summaries(userIds));
    }

    /**
     * {@code GET /internal/admin/overview-stats}：提供 IAM 用户、角色和权限的汇总统计。
     * <p>要求 SCOPE_internal；返回 IamOverviewStats，由 Project 管理概览组合其本地授权与审计统计。
     * <p>仅返回统计摘要，不提供用户明细，也不执行管理配置变更。
     */
    @GetMapping("/admin/overview-stats")
    ApiResponse<IamOverviewStats> overviewStats() {
        return ApiResponse.ok(overview.overview());
    }

    /**
     * {@code GET /internal/departments/{deptId}/summary}：为项目申请或归属修复查询部门名称和有效状态。
     * <p>要求 SCOPE_internal；deptId 为 IAM 部门主键，返回 DepartmentSummary 的 ID、名称和 enabled。
     * <p>部门不存在返回 404；调用方仍需检查 enabled，返回摘要本身不保证该部门可用于创建项目。
     */
    @GetMapping("/departments/{deptId}/summary")
    ApiResponse<com.amatrix.sic.contracts.DepartmentSummary> department(
            @org.springframework.web.bind.annotation.PathVariable Long deptId) {
        var row = departments.department(deptId);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found");
        return ApiResponse.ok(new com.amatrix.sic.contracts.DepartmentSummary(deptId,
                (String) row.get("deptName"), Boolean.TRUE.equals(row.get("enabled"))));
    }
}
