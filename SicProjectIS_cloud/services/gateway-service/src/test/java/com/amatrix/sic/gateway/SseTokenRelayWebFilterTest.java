package com.amatrix.sic.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class SseTokenRelayWebFilterTest {
    private final SseTokenRelayWebFilter filter = new SseTokenRelayWebFilter();

    @Test
    void convertsQueryTokenToAuthorizationAndRemovesItFromForwardedUrl() {
        ServerWebExchange forwarded = filter(MockServerHttpRequest
                .get("/api/sse/subscribe?projectId=7&token=header.payload.signature").build());

        assertThat(forwarded.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer header.payload.signature");
        assertThat(forwarded.getRequest().getQueryParams()).containsEntry("projectId", java.util.List.of("7"));
        assertThat(forwarded.getRequest().getQueryParams()).doesNotContainKey("token");
        assertThat(forwarded.getRequest().getURI().getRawQuery()).isEqualTo("projectId=7");
    }

    @Test
    void removesQueryTokenEvenWhenAuthorizationHeaderAlreadyExists() {
        ServerWebExchange forwarded = filter(MockServerHttpRequest
                .get("/api/sse/subscribe?token=query-token")
                .header(HttpHeaders.AUTHORIZATION, "Bearer header-token")
                .build());

        assertThat(forwarded.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer header-token");
        assertThat(forwarded.getRequest().getQueryParams()).doesNotContainKey("token");
        assertThat(forwarded.getRequest().getURI().getRawQuery()).isNull();
    }

    @Test
    void leavesNonSseRequestsUntouched() {
        var request = MockServerHttpRequest.get("/api/projects?token=ordinary-query").build();
        ServerWebExchange forwarded = filter(request);

        assertThat(forwarded.getRequest()).isSameAs(request);
        assertThat(forwarded.getRequest().getQueryParams().getFirst("token")).isEqualTo("ordinary-query");
    }

    private ServerWebExchange filter(MockServerHttpRequest request) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(request), chain).block();
        return forwarded.get();
    }
}
