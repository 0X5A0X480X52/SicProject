package com.amatrix.sic.gateway.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import reactor.core.publisher.Mono;

/**
 * 在 JWT 校验后查询 Redis 的注销 jti 与用户会话版本，判定会话是否仍然有效。
 * JWT 签名有效不代表会话仍有效；pending 或 Redis 故障会标记基础设施不可用，供 Gateway 区分撤销与依赖故障。
 */
@Component
public class SessionValidityAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    // 标记会话授权管理器不可用，返回 503 服务不可用
    public static final String UNAVAILABLE = SessionValidityAuthorizationManager.class.getName() + ".unavailable";

    // redis Lua 脚本：检查撤销、版本和 pending 状态，返回 -2=pending、0=无效、1=有效
    // KEYS[1] = sic:jwt:revoked:{jti}
    // KEYS[2] = sic:jwt:version:{subject}
    // KEYS[3] = sic:jwt:pending:{subject}
    // ARGV[1] = JWT 中的 authVersion
    static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> CHECK =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('EXISTS',KEYS[3]) == 1 then return -2 end
                    if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
                    if redis.call('GET',KEYS[2]) == ARGV[1] then return 1 end
                    return 0
                    """, Long.class);
    private final ReactiveStringRedisTemplate redis;

    public SessionValidityAuthorizationManager(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 在一个 Redis Lua 检查中读取撤销、版本和 pending 状态。依赖故障仍拒绝访问，同时标记不可用原因，避免被外层误转换成普通登录失效。
     */
    @Override
    public Mono<AuthorizationResult> authorize(
            Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                // 过滤掉未认证和非 JWT 的请求
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                // 将 Authentication 转换为 JwtAuthenticationToken
                .cast(JwtAuthenticationToken.class)
                // 使用 Redis 检查会话有效性
                .flatMap(token -> {
                    String jti = token.getToken().getId();
                    String subject = token.getToken().getSubject();
                    long tokenVersion = authVersion(token);
                    // 如果 jti 或 subject 为空，或者 tokenVersion 小于等于 0，则直接返回 false
                    if (jti == null || jti.isBlank() || subject == null || subject.isBlank() || tokenVersion <= 0) {
                        return Mono.just(false);
                    }
                    // 执行 Redis Lua 脚本检查撤销、版本和 pending 状态
                    return redis.execute(CHECK, java.util.List.of("sic:jwt:revoked:" + jti,
                                    "sic:jwt:version:" + subject, "sic:jwt:pending:" + subject),
                                    java.util.List.of(Long.toString(tokenVersion))).single()
                            .map(result -> {
                                if (result == -2) markUnavailable(context); // pending 状态，标记不可用
                                return result == 1;                         // 1=有效，0=无效
                            });
                })
                // 将布尔值转换为授权结果
                .map(allowed -> (AuthorizationResult) new AuthorizationDecision(allowed))
                .defaultIfEmpty(new AuthorizationDecision(false))
                .onErrorResume(error -> {
                    markUnavailable(context);
                    return Mono.just(new AuthorizationDecision(false));
                });
    }

    private static void markUnavailable(AuthorizationContext context) {
        context.getExchange().getAttributes().put(UNAVAILABLE, true);
    }

    private long claimLong(JwtAuthenticationToken token, String claim) {
        Object value = token.getToken().getClaim(claim);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private long authVersion(JwtAuthenticationToken token) {
        long canonical = claimLong(token, "authVersion");
        return canonical > 0 ? canonical : claimLong(token, "ver");
    }

}
