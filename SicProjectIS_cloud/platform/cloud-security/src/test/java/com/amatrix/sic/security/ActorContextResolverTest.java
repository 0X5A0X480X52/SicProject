package com.amatrix.sic.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ActorContextResolverTest {
    private final ActorContextResolver resolver = new ActorContextResolver();

    @Test
    void convertsJwtClaimsToAnImmutableActorContext() {
        Jwt jwt = jwt("42", Map.of("username", "alice", "deptId", 7L, "deptName", "Science",
                "roles", List.of("DEPT_ADMIN"), "permissions", List.of("PROJECT_READ"), "authVersion", 3L));

        var actor = resolver.from(new JwtAuthenticationToken(jwt));

        assertThat(actor.userId()).isEqualTo(42L);
        assertThat(actor.username()).isEqualTo("alice");
        assertThat(actor.deptId()).isEqualTo(7L);
        assertThat(actor.roleCodes()).containsExactly("DEPT_ADMIN");
        assertThat(actor.permissionCodes()).containsExactly("PROJECT_READ");
        assertThat(actor.tokenId()).isEqualTo("jwt-id");
        assertThat(actor.authVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsNonJwtAndMalformedSubjectsWithoutLeakingExceptionsIntoSpel() {
        assertThat(resolver.from(UsernamePasswordAuthenticationToken.unauthenticated("actor", "n/a"))).isNull();
        assertThat(resolver.from(new JwtAuthenticationToken(jwt("not-a-number", Map.of())))).isNull();
    }

    @Test
    void acceptsTheLegacyVersionClaimDuringRollingMigration() {
        var actor = resolver.from(new JwtAuthenticationToken(jwt("42", Map.of("ver", 2L))));
        assertThat(actor.authVersion()).isEqualTo(2L);
    }

    private static Jwt jwt(String subject, Map<String, Object> claims) {
        var allClaims = new java.util.LinkedHashMap<String, Object>(claims);
        allClaims.put("sub", subject);
        allClaims.put("jti", "jwt-id");
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), allClaims);
    }
}
