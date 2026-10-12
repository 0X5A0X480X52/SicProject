package com.amatrix.sic.gateway;

import java.util.concurrent.TimeoutException;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 将网关未处理异常映射为 JSON 错误响应。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayJsonErrorHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper mapper;

    public GatewayJsonErrorHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        HttpStatusCode status = status(error);
        String message = error instanceof ResponseStatusException response && response.getReason() != null
                ? response.getReason()
                : defaultMessage(status);
        return GatewayErrorWriter.write(exchange, mapper, status, message);
    }

    private static HttpStatusCode status(Throwable error) {
        if (error instanceof ErrorResponse response) return response.getStatusCode();
        if (error instanceof TimeoutException) return HttpStatus.SERVICE_UNAVAILABLE;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String defaultMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Resource not found";
            case 429 -> "Too many requests";
            case 503 -> "Service unavailable";
            default -> status.is5xxServerError() ? "Internal server error" : "Request failed";
        };
    }
}
