package com.amatrix.sic.gateway.filter;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 记录路径/状态/耗时/用户与关联标识，并汇总到请求日志。
 */
@Component
public class GatewayRequestLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long started = System.nanoTime();
        return exchange.getPrincipal().map(GatewayRequestLoggingFilter::userId).defaultIfEmpty("-")
                .flatMap(userId -> chain.filter(exchange).doFinally(signal -> {
                    String traceId = safe(exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_HEADER));
                    String sagaId = safe(exchange.getRequest().getHeaders().getFirst("X-Saga-Id"));
                    String eventId = safe(exchange.getRequest().getHeaders().getFirst("X-Event-Id"));
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    log.info("gateway_request trace={} saga={} event={} user={} method={} path={} status={} elapsedMs={}",
                            traceId, sagaId, eventId, userId, exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getRawPath(), exchange.getResponse().getStatusCode(), elapsedMs);
                }));
    }

    private static String userId(Principal principal) {
        return principal instanceof JwtAuthenticationToken jwt ? safe(jwt.getToken().getSubject())
                : safe(principal.getName());
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9:_-]{1,160}")) return "-";
        return value;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
