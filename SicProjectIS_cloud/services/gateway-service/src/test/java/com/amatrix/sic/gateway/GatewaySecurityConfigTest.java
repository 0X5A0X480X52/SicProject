package com.amatrix.sic.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

class GatewaySecurityConfigTest {
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        SessionValidityAuthorizationManager sessions = mock(SessionValidityAuthorizationManager.class);
        when(sessions.authorize(any(), any())).thenReturn(Mono.just(new AuthorizationDecision(false)));
        var chain = new GatewaySecurityConfig().gatewaySecurityFilterChain(ServerHttpSecurity.http(), sessions,
                mock(ReactiveJwtDecoder.class), new ObjectMapper());
        client = WebTestClient.bindToController(new TestEndpoints())
                .webFilter(new TraceIdWebFilter(), new WebFilterChainProxy(chain)).apply(springSecurity()).build();
    }

    @Test
    void returnsExistingJsonWrapperForUnauthenticatedApiCalls() {
        client.get().uri("/api/projects").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(TraceIdWebFilter.TRACE_HEADER, "[A-Za-z0-9_-]{8,80}")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.data").doesNotExist()
                .jsonPath("$.message").isEqualTo("Authentication is required");
    }

    @Test
    void treatsAnAuthenticatedButInvalidSessionAsUnauthorized() {
        client.mutateWith(mockJwt()).get().uri("/api/projects").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(TraceIdWebFilter.TRACE_HEADER, "[A-Za-z0-9_-]{8,80}")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.data").doesNotExist()
                .jsonPath("$.message").isEqualTo("Authentication is required");
    }

    @Test
    void permitsLoginButNeverRoutesInternalEndpointsFromAUserToken() {
        client.post().uri("/api/auth/login").exchange().expectStatus().isOk();

        client.mutateWith(mockJwt()).get().uri("/internal/secret").exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Access denied");
    }

    @Test
    void exposesOnlyRequiredMonitoringEndpoints() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
        client.mutateWith(mockJwt()).get().uri("/actuator/env").exchange().expectStatus().isForbidden();
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/projects", "/internal/secret", "/actuator/health", "/actuator/prometheus",
                "/actuator/env"})
        Mono<String> get() {
            return Mono.just("ok");
        }

        @PostMapping("/api/auth/login")
        Mono<String> login() {
            return Mono.just("ok");
        }
    }
}
