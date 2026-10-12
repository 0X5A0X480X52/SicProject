package com.amatrix.sic.material.client;

import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.amatrix.sic.contracts.ApiResponse;

/**
 * Feign 向 IAM 换取 material-service 内部 Token。
 */
@FeignClient(name="iam-service",contextId="materialTokenClient")
public interface MaterialTokenClient{
    /**
     * {@code POST /internal/tokens/service}：使用 bootstrap secret 兑换 material-service 内部 token。
     * <p>此请求不携带已有 Bearer token；secret 位于 X-Service-Bootstrap-Secret，serviceName 由调用方固定为 material-service。
     * <p>返回 token、expiresAt、tokenId，TokenProvider 缓存并提前 60 秒刷新；错误凭据返回 401，不能降级为用户 token。
     */
    @PostMapping("/internal/tokens/service") ApiResponse<Token> token(@RequestHeader("X-Service-Bootstrap-Secret")String secret,@RequestParam("serviceName")String serviceName);

    /** IAM 内部 token 传输结构；token 是敏感 JWT，expiresAt 用于本地刷新，tokenId 是 jti。 */
    record Token(String token,Instant expiresAt,String tokenId){}
}
