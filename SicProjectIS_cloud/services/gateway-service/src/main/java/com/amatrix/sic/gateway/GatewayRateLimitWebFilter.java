package com.amatrix.sic.gateway;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 按来源 IP 与每秒窗口在 Redis 计数限流；Redis 失败时放行，超限返回 429。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayRateLimitWebFilter implements WebFilter {
    private final ReactiveStringRedisTemplate redis;
    private final GatewayRateLimitProperties properties;
    private final ObjectMapper mapper;

    public GatewayRateLimitWebFilter(ReactiveStringRedisTemplate redis, GatewayRateLimitProperties properties,
            ObjectMapper mapper) {
        this.redis = redis;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!properties.enabled() || path.startsWith("/actuator/")) return chain.filter(exchange);
        long window = System.currentTimeMillis() / 1000;
        String key = "sic:gateway:rate:" + client(exchange) + ":" + window;
        return redis.opsForValue().increment(key).onErrorReturn(-1L).flatMap(count -> {
            if (count < 0) return chain.filter(exchange);
            Mono<Void> expiry = count == 1
                    ? redis.expire(key, Duration.ofSeconds(2)).then().onErrorResume(error -> Mono.empty())
                    : Mono.empty();
            return expiry.then(count > properties.requestsPerSecond()
                    ? GatewayErrorWriter.write(exchange, mapper, HttpStatus.TOO_MANY_REQUESTS, "Too many requests")
                    : chain.filter(exchange));
        });
    }

    private static String client(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return safe(remote == null ? "unknown" : remote.getAddress().getHostAddress());
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9:._-]", "_");
    }
}
