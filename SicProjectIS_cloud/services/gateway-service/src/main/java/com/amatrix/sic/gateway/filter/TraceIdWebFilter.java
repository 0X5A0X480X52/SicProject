package com.amatrix.sic.gateway.filter;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * 在安全链可能提前返回之前规范化/生成 X-Trace-Id，同时写入下游请求和响应。
 * 保证每个请求都有一个可追踪的唯一 ID，便于日志关联和问题排查。
 */
@Component
public class TraceIdWebFilter implements WebFilter, Ordered {
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        String traceId = supplied != null && supplied.matches("[A-Za-z0-9_-]{8,80}")
                ? supplied
                : UUID.randomUUID().toString();
        ServerWebExchange traced = exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> headers.set(TRACE_HEADER, traceId)).build())
                .build();
        traced.getResponse().beforeCommit(() -> {
            traced.getResponse().getHeaders().set(TRACE_HEADER, traceId);
            traced.getResponse().getHeaders().remove(HttpHeaders.SERVER);
            return Mono.empty();
        });
        return chain.filter(traced);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
