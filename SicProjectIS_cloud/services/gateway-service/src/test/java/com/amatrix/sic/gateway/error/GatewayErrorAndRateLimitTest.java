package com.amatrix.sic.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;

import com.amatrix.sic.gateway.config.properties.GatewayRateLimitProperties;
import com.amatrix.sic.gateway.filter.GatewayRateLimitWebFilter;
import com.amatrix.sic.gateway.filter.TraceIdWebFilter;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

class GatewayErrorAndRateLimitTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void wrapsGatewayServiceUnavailableErrors() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/projects"));

        new GatewayJsonErrorHandler(mapper).handle(exchange,
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Project service unavailable")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdWebFilter.TRACE_HEADER)).isNotBlank();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"success\":false", "\"data\":null", "Project service unavailable");
    }

    @Test
    void wrapsGatewayNotFoundErrors() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing"));

        new GatewayJsonErrorHandler(mapper).handle(exchange,
                new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"success\":false", "\"data\":null", "Resource not found");
    }

    @Test
    void returnsWrapped429WithoutCallingDownstream() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(Mono.just(41L));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/projects")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 31000)));

        new GatewayRateLimitWebFilter(redis, new GatewayRateLimitProperties(true, 40), mapper)
                .filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Too many requests");
        verifyNoInteractions(chain);
    }

    @Test
    void failsOpenWhenRedisIsTemporarilyUnavailable() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/projects"));

        new GatewayRateLimitWebFilter(redis, new GatewayRateLimitProperties(true, 40), mapper)
                .filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
