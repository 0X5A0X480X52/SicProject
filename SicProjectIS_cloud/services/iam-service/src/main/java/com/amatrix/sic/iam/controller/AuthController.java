package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.AuthApplicationService;
import com.amatrix.sic.iam.dto.IamDtos;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.iam.dto.IamDtos.CurrentUser;
import com.amatrix.sic.iam.dto.IamDtos.LoginRequest;
import com.amatrix.sic.iam.dto.IamDtos.LoginResponse;
import com.amatrix.sic.iam.dto.IamDtos.RegisterRequest;

/**
 * /api/auth 的 login/register/me/logout HTTP 入口，提取 JWT 用户/jti/过期时间并委派业务服务。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthApplicationService service;

    public AuthController(AuthApplicationService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/auth/login}：使用用户名和密码登录，取得用户 JWT 和前端会话信息。
     * <p>公开接口；JSON 请求体提供 username、password。服务验证账号存在、启用及密码匹配。
     * <p>返回 token、expiresAt、user（CurrentUser）；签发时使用当前 authVersion 并发布登录版本，不能用旧权限版本覆盖撤权状态。
     * <p>账号或密码不匹配返回 401；依赖故障不代表凭据失效，调用方应区分登录失败与基础设施错误。
     */
    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request.username(), request.password()));
    }

    /**
     * {@code POST /api/auth/register}：注册普通业务账号，并直接返回登录结果。
     * <p>公开接口；JSON 请求体包含用户名、密码、真实姓名、联系方式及可选 deptId，具体字段见 RegisterRequest。
     * <p>创建账号并返回 token、expiresAt、user；当前注册流程不自动分配业务角色，角色管理和专家资格审批使用独立接口。
     * <p>密码至少六位，用户名重复返回 409；realName 缺省使用用户名。
     * <p>注册时的可选部门不替代项目申请校验；申请项目仍要求 IAM 当前有效部门。
     */
    @PostMapping("/register")
    ApiResponse<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(service.register(request));
    }

    /**
     * {@code GET /api/auth/me}：读取当前登录账号的最新身份资料、部门、角色和权限。
     * <p>账号 ID 取自已验证 JWT 的 subject，不接受客户端指定其他用户。
     * <p>返回 CurrentUser，身份资料由 IAM 当前记录组装；JWT 本身携带的角色不是这次查询的数据源。
     * <p>账号不存在或停用时返回 401，前端可据此清理会话。
     */
    @GetMapping("/me")
    ApiResponse<CurrentUser> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.current(Long.valueOf(jwt.getSubject())).currentUser());
    }

    /**
     * {@code POST /api/auth/logout}：撤销本次登录 token，结束当前会话。
     * <p>从 JWT 读取 jti 和 expiresAt，在 Redis 保存撤销标记直到 token 到期。
     * <p>成功返回 ApiResponse<Void>，data 为 null；不通过此入口撤销该账号的其他 token。
     * <p>前端还需清理本地会话，后续请求由 Gateway 检查撤销标记。
     */
    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        service.logout(jwt.getId(), jwt.getExpiresAt());
        return ApiResponse.ok(null);
    }
}
