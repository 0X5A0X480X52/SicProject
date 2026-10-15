package com.amatrix.sic.iam.service;

import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.dto.IamDtos;
import com.amatrix.sic.iam.security.JwtTokenIssuer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.amatrix.sic.iam.dto.IamDtos.UserAccount;

class AuthApplicationServiceTest {

    @Test
    void loginPublishesCurrentAuthorizationVersionToRedis() {
        IamRepository repository = mock(IamRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtTokenIssuer tokens = mock(JwtTokenIssuer.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        UserAccount user = user(12L, 4L);
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(repository.findByUsername("alice")).thenReturn(user);
        when(passwords.matches("secret123", user.passwordHash())).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new JwtTokenIssuer.IssuedToken("jwt", expiresAt, "jti"));
        when(redis.opsForValue()).thenReturn(values);
        SessionVersionService versions = mock(SessionVersionService.class);

        var response = new AuthApplicationService(repository, passwords, tokens, redis, versions)
                .login("  alice ", "secret123");

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().userId()).isEqualTo(12L);
        verify(versions).publishLogin(12L, 4L);
    }

    @Test
    void logoutBlacklistsJtiOnlyForItsRemainingLifetime() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AuthApplicationService service = new AuthApplicationService(mock(IamRepository.class),
                mock(PasswordEncoder.class), mock(JwtTokenIssuer.class), redis, mock(SessionVersionService.class));
        Instant expiresAt = Instant.now().plusSeconds(120);

        service.logout("token-123", expiresAt);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("sic:jwt:revoked:token-123"), eq("1"), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(120));

        service.logout("already-expired", Instant.now().minusSeconds(1));
        verify(values, never()).set(eq("sic:jwt:revoked:already-expired"), eq("1"), any(Duration.class));
    }

    private static UserAccount user(long id, long version) {
        return new UserAccount(id, "alice", "encoded", "Alice", 3L, "Science", null, null,
                true, version, List.of("PROJECT_LEADER"), List.of("project:create"));
    }
}
