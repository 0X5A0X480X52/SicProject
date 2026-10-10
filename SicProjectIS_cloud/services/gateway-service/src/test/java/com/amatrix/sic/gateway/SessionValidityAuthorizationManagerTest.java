package com.amatrix.sic.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import reactor.core.publisher.Mono;

class SessionValidityAuthorizationManagerTest {
    private ReactiveStringRedisTemplate redis;
    private ReactiveValueOperations<String, String> values;
    private SessionValidityAuthorizationManager manager;

    @BeforeEach
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> mockedValues = mock(ReactiveValueOperations.class);
        values = mockedValues;
        when(redis.opsForValue()).thenReturn(values);
        manager = new SessionValidityAuthorizationManager(redis);
    }

    @Test
    void acceptsOnlyARegisteredCurrentAndNonRevokedSession() {
        when(redis.hasKey("sic:jwt:revoked:token-1")).thenReturn(Mono.just(false));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.just("7"));

        assertThat(authorize(jwt("token-1", "42", 7))).isTrue();
    }

    @Test
    void acceptsTheLegacyVersionClaimDuringRollingMigration() {
        when(redis.hasKey("sic:jwt:revoked:legacy")).thenReturn(Mono.just(false));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.just("7"));

        assertThat(authorize(jwt("legacy", "42", 7, "ver"))).isTrue();
    }

    @Test
    void rejectsRevokedOrSupersededSessions() {
        when(redis.hasKey("sic:jwt:revoked:revoked")).thenReturn(Mono.just(true));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.just("7"));
        assertThat(authorize(jwt("revoked", "42", 7))).isFalse();

        when(redis.hasKey("sic:jwt:revoked:old")).thenReturn(Mono.just(false));
        assertThat(authorize(jwt("old", "42", 6))).isFalse();
    }

    @Test
    void failsClosedForMissingCorruptOrUnavailableVersionState() {
        when(redis.hasKey("sic:jwt:revoked:missing")).thenReturn(Mono.just(false));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.empty());
        assertThat(authorize(jwt("missing", "42", 7))).isFalse();

        when(redis.hasKey("sic:jwt:revoked:corrupt")).thenReturn(Mono.just(false));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.just("not-a-number"));
        assertThat(authorize(jwt("corrupt", "42", 7))).isFalse();

        when(redis.hasKey("sic:jwt:revoked:unavailable"))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        when(values.get("sic:jwt:version:42")).thenReturn(Mono.just("7"));
        assertThat(authorize(jwt("unavailable", "42", 7))).isFalse();
    }

    @Test
    void rejectsTokensMissingMandatorySessionClaimsWithoutCallingRedis() {
        assertThat(authorize(jwt(null, "42", 7))).isFalse();
        assertThat(authorize(jwt("token", "42", 0))).isFalse();
        verifyNoInteractions(values);
    }

    private boolean authorize(JwtAuthenticationToken authentication) {
        AuthorizationResult result = manager.authorize(Mono.just(authentication), mock(AuthorizationContext.class))
                .block();
        return result != null && result.isGranted();
    }

    private static JwtAuthenticationToken jwt(String tokenId, String subject, long version) {
        return jwt(tokenId, subject, version, "authVersion");
    }

    private static JwtAuthenticationToken jwt(String tokenId, String subject, long version, String versionClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("encoded-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(600))
                .audience(List.of("sic-api"))
                .claim(versionClaim, version);
        if (tokenId != null) builder.claim("jti", tokenId);
        return new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
