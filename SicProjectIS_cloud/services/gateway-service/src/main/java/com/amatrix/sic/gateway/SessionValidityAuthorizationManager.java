package com.amatrix.sic.gateway;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import reactor.core.publisher.Mono;

@Component
public class SessionValidityAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {
    private final ReactiveStringRedisTemplate redis;

    public SessionValidityAuthorizationManager(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<AuthorizationResult> authorize(
            Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> {
                    String jti = token.getToken().getId();
                    String subject = token.getToken().getSubject();
                    long tokenVersion = authVersion(token);
                    if (jti == null || jti.isBlank() || subject == null || subject.isBlank() || tokenVersion <= 0) {
                        return Mono.just(false);
                    }
                    Mono<Boolean> revoked = redis.hasKey("sic:jwt:revoked:" + jti);
                    Mono<String> currentVersion = redis.opsForValue().get("sic:jwt:version:" + subject);
                    return Mono.zip(revoked, currentVersion)
                            .map(values -> !values.getT1()
                                    && tokenVersion == parseLong(values.getT2()));
                })
                .map(allowed -> (AuthorizationResult) new AuthorizationDecision(allowed))
                .defaultIfEmpty(new AuthorizationDecision(false))
                .onErrorReturn(new AuthorizationDecision(false));
    }

    private long claimLong(JwtAuthenticationToken token, String claim) {
        Object value = token.getToken().getClaim(claim);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private long authVersion(JwtAuthenticationToken token) {
        long canonical = claimLong(token, "authVersion");
        return canonical > 0 ? canonical : claimLong(token, "ver");
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
