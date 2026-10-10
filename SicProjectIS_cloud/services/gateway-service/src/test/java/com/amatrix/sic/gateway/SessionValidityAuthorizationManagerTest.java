package com.amatrix.sic.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SessionValidityAuthorizationManagerTest {
    @Test void acceptsCanonicalAndLegacyVersionsUsingOneAtomicRedisCheck() {
        for (String claim : List.of("authVersion", "ver")) {
            var redis = mock(ReactiveStringRedisTemplate.class);
            when(redis.execute(eq(SessionValidityAuthorizationManager.CHECK), eq(keys()), eq(List.of("7"))))
                    .thenReturn(Flux.just(1L));
            var context = context();
            assertThat(new SessionValidityAuthorizationManager(redis).authorize(Mono.just(jwt(claim,7)),context)
                    .block().isGranted()).isTrue();
            verify(redis).execute(eq(SessionValidityAuthorizationManager.CHECK),eq(keys()),eq(List.of("7")));
            verifyNoMoreInteractions(redis);
        }
    }

    @Test void rejectsRevokedMissingCorruptAndSupersededSessions() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(eq(SessionValidityAuthorizationManager.CHECK),eq(keys()),eq(List.of("7"))))
                .thenReturn(Flux.just(0L));
        var context = context();
        assertThat(new SessionValidityAuthorizationManager(redis).authorize(Mono.just(jwt("authVersion",7)),context)
                .block().isGranted()).isFalse();
        assertThat(context.getExchange().getAttributes()).doesNotContainKey(SessionValidityAuthorizationManager.UNAVAILABLE);
    }

    @Test void fencesAndInfrastructureErrorsDenyAccessWithAnUnavailableMarker() {
        for (Flux<Long> outcome : List.of(Flux.just(-2L),Flux.<Long>error(new IllegalStateException("unavailable")))) {
            var redis = mock(ReactiveStringRedisTemplate.class);
            when(redis.execute(eq(SessionValidityAuthorizationManager.CHECK),eq(keys()),eq(List.of("7"))))
                    .thenReturn(outcome);
            var context = context();
            assertThat(new SessionValidityAuthorizationManager(redis).authorize(Mono.just(jwt("authVersion",7)),context)
                    .block().isGranted()).isFalse();
            assertThat((Object)context.getExchange().getAttribute(SessionValidityAuthorizationManager.UNAVAILABLE)).isEqualTo(true);
        }
    }

    @Test void missingClaimsAreRejectedWithoutRedis() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        assertThat(new SessionValidityAuthorizationManager(redis).authorize(Mono.just(jwt("authVersion",0)),context())
                .block().isGranted()).isFalse();
        verifyNoInteractions(redis);
    }

    private static List<String> keys() { return List.of("sic:jwt:revoked:token","sic:jwt:version:42","sic:jwt:pending:42"); }
    private static AuthorizationContext context() {
        return new AuthorizationContext(MockServerWebExchange.from(MockServerHttpRequest.get("/api/projects").build()));
    }
    private static JwtAuthenticationToken jwt(String claim,long version) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg","RS256").subject("42")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).claim("jti","token")
                .claim(claim,version).build(), List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
    }
}
