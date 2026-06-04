package com.amatrix.sicprojectis_backend.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldIssueAndParseToken() {
        JwtTokenService service = new JwtTokenService(new JwtProperties("test-secret-key-with-more-than-thirty-two-bytes", 30), clock);

        JwtTokenService.TokenIssueResult token = service.issueToken(1L, "alice", List.of("PROJECT_LEADER"));

        AuthenticatedUser user = service.parse(token.token());
        assertThat(user.userId()).isEqualTo(1L);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.roleCodes()).containsExactly("PROJECT_LEADER");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-06-03T00:30:00Z"));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenService issuer = new JwtTokenService(new JwtProperties("test-secret-key-with-more-than-thirty-two-bytes", -1), clock);
        String token = issuer.issueToken(1L, "alice", List.of("PROJECT_LEADER")).token();
        JwtTokenService parser = new JwtTokenService(new JwtProperties("test-secret-key-with-more-than-thirty-two-bytes", 30), clock);

        assertThatThrownBy(() -> parser.parse(token)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }
}
