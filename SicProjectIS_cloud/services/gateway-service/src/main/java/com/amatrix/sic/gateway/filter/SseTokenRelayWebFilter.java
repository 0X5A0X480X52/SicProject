package com.amatrix.sic.gateway.filter;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

/**
 * 把 SSE 订阅 query 中的 token 转成 Bearer Header 并移除 URL 中的 token。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SseTokenRelayWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 只处理 SSE 订阅请求，其他请求直接放行
        if (!"/api/sse/subscribe".equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        // 如果请求中没有 token 参数，直接放行
        String token = exchange.getRequest().getQueryParams().getFirst("token");
        if (!exchange.getRequest().getQueryParams().containsKey("token")) {
            return chain.filter(exchange);
        }
        // 构建新的 URI，移除 token 参数
        URI uri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParam("token")
                .build(true)
                .toUri();
        var request = exchange.getRequest().mutate().uri(uri);
        // 如果 token 不为空且请求头中没有 Authorization，则添加 Bearer Header
        if (token != null && !token.isBlank()
                && !exchange.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return chain.filter(exchange.mutate().request(request.build()).build());
    }
}
