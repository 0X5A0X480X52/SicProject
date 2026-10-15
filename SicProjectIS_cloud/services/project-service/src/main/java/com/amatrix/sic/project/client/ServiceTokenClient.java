package com.amatrix.sic.project.client;

import java.time.Instant;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.amatrix.sic.contracts.ApiResponse;

/**
 * Feign 通过 bootstrap secret 向 IAM 换取 project-service 内部 Token。
 */
@FeignClient(name = "iam-service", contextId = "projectServiceTokenClient")
public interface ServiceTokenClient {
    /**
     * {@code POST /internal/tokens/service}：向 IAM 兑换 project-service 的内部调用 token。
     * <p>secret 通过 X-Service-Bootstrap-Secret 传递，serviceName 固定由调用方提供 {@code project-service}；
     * 此入口不使用已有 Bearer token，凭据错误返回 401。
     * <p>返回 token、expiresAt、tokenId；TokenProvider 会缓存 token 并在到期前刷新，调用业务内部接口时再拼接 Bearer 前缀。
     */
    @PostMapping("/internal/tokens/service")
    ApiResponse<TokenResponse> token(@RequestHeader("X-Service-Bootstrap-Secret") String secret,
            @RequestParam("serviceName") String serviceName);

    /**
     * IAM 服务 token 的传输结构；tokenId 是 JWT jti，expiresAt 用于本地提前刷新。
     * token 字段是敏感凭据，不应写入日志或业务响应。
     */
    record TokenResponse(String token, Instant expiresAt, String tokenId) {
    }
}
