package com.amatrix.sic.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.amatrix.sic.contracts.ActorContext;

/**
 * 从方法安全 Authentication 解析 JWT 操作人；非 JWT 或格式错误返回 null，供权限注解的 SpEL 使用。
 */
public final class ActorContextResolver {
    public ActorContext from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) return null;
        try {
            return ActorContextFactory.from(token.getToken());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
