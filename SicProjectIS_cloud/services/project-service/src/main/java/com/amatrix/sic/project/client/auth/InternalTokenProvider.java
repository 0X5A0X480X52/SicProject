package com.amatrix.sic.project.client.auth;

import com.amatrix.sic.project.client.ServiceTokenClient;
import com.amatrix.sic.project.config.properties.ServiceClientProperties;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * 同步缓存内部 Token，在过期前 60 秒刷新，返回 Bearer Header。
 */
@Component
public class InternalTokenProvider {
    private final ServiceTokenClient client;
    private final ServiceClientProperties properties;
    private volatile ServiceTokenClient.TokenResponse cached;

    public InternalTokenProvider(ServiceTokenClient client, ServiceClientProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 返回 project-service 调用内部 API 使用的完整 Bearer Header。
     * <p>首次调用或 token 距离 expiresAt 不足 60 秒时，同步调用 IAM 换取新 token；其余请求复用 volatile 缓存。
     * 同步方法避免同一实例并发刷新，但不在多副本之间共享 token。
     * <p>IAM 返回 success=false 或空 data 时抛出异常，调用方不会回退到用户 token、旧过期 token或匿名调用。
     */
    public synchronized String authorization() {
        if (cached == null || cached.expiresAt().isBefore(Instant.now().plusSeconds(60))) {
            var response = client.token(properties.bootstrapSecret(), "project-service");
            if (!response.success() || response.data() == null) {
                throw new IllegalStateException("IAM did not issue a project-service token");
            }
            cached = response.data();
        }
        return "Bearer " + cached.token();
    }
}
