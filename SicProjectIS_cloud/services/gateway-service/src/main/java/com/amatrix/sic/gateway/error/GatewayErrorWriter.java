package com.amatrix.sic.gateway.error;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.gateway.filter.TraceIdWebFilter;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 统一写出网关错误的 JSON 响应体，保持 {success,data,message} 形状。
 */
public final class GatewayErrorWriter {
    private GatewayErrorWriter() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, ObjectMapper mapper, HttpStatusCode status, String message) {
        if (exchange.getResponse().isCommitted()) return Mono.error(new IllegalStateException(message));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_HEADER);
        if (traceId == null || !traceId.matches("[A-Za-z0-9_-]{8,80}")) traceId = UUID.randomUUID().toString();
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceIdWebFilter.TRACE_HEADER, traceId);
        try {
            byte[] body = mapper.writeValueAsString(ApiResponse.error(message)).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            return exchange.getResponse().setComplete();
        }
    }
}
