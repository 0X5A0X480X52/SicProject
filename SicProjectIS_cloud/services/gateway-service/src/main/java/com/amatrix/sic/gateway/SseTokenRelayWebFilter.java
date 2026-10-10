package com.amatrix.sic.gateway;

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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SseTokenRelayWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!"/api/sse/subscribe".equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getQueryParams().getFirst("token");
        if (!exchange.getRequest().getQueryParams().containsKey("token")) {
            return chain.filter(exchange);
        }
        URI uri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParam("token")
                .build(true)
                .toUri();
        var request = exchange.getRequest().mutate().uri(uri);
        if (token != null && !token.isBlank()
                && !exchange.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return chain.filter(exchange.mutate().request(request.build()).build());
    }
}
