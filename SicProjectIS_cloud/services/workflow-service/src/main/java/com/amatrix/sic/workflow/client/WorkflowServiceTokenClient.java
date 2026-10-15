package com.amatrix.sic.workflow.client;

import java.time.Instant;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.UserSummary;

/**
 * Feign 调 IAM 获取内部 Token 和用户摘要，用于内部调用与专家资格检查。
 */
@FeignClient(name="iam-service",contextId="workflowServiceTokenClient")
public interface WorkflowServiceTokenClient {
    /**
     * {@code POST /internal/users/summaries}：批量读取 IAM 当前用户摘要。
     * <p>Authorization 使用 workflow-service 内部 Bearer token；请求体的用户 ID 去重后最多包含 100 个有效值。
     * <p>返回列表只包含存在用户，调用方需按 userId 关联并检查缺失项；用于批量身份核对，避免逐用户远程请求。
     */
    @PostMapping("/internal/users/summaries")
    ApiResponse<java.util.List<UserSummary>> summaries(@RequestHeader("Authorization") String authorization,
            @org.springframework.web.bind.annotation.RequestBody java.util.List<Long> userIds);

    /**
     * {@code POST /internal/tokens/service}：使用 bootstrap secret 兑换 workflow-service 内部 token。
     * <p>此请求不携带已有 Bearer token；secret 放在 X-Service-Bootstrap-Secret，serviceName 由调用方固定为 workflow-service。
     * <p>返回 token、expiresAt、tokenId，TokenProvider 缓存并提前 60 秒刷新；凭据及返回 token 均不得写入日志。
     */
    @PostMapping("/internal/tokens/service")
    ApiResponse<Token> token(@RequestHeader("X-Service-Bootstrap-Secret")String secret,
            @RequestParam("serviceName")String serviceName);

    /**
     * {@code GET /internal/users/{userId}/summary}：读取一名用户的 IAM 当前摘要。
     * <p>Authorization 使用内部 Bearer token；专家指派前用它验证用户存在、启用且具有 EXPERT 角色。
     * <p>返回 UserSummary，不包含密码；用户不存在为 404，网络或 IAM 故障不会降级为“用户不存在”。
     */
    @GetMapping("/internal/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@RequestHeader("Authorization") String authorization,
            @PathVariable Long userId);

    /** IAM 内部 token 传输结构；expiresAt 用于刷新，tokenId 是 JWT jti。 */
    record Token(String token, Instant expiresAt, String tokenId){}
}
