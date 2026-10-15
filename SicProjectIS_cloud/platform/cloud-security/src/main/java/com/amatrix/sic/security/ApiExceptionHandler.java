package com.amatrix.sic.security;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;

/**
 * Servlet 全局异常转换：业务 HTTP 状态、权限拒绝、参数绑定错误及未预期异常，统一 ApiResponse。
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> status(ResponseStatusException ex) {
        String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception ex) {
        var result = ex instanceof MethodArgumentNotValidException method
                ? method.getBindingResult()
                : ((BindException) ex).getBindingResult();
        String message = result.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        boolean dependencyFailure = false;
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = ex; cause != null && seen.add(cause); cause = cause.getCause()) {
            String type = cause.getClass().getName();
            dependencyFailure |= type.startsWith("feign.") || type.startsWith("io.github.resilience4j.")
                    || type.equals("org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException");
            // 保留业务冲突和校验状态；内部服务凭据失败不能被解释为用户 JWT 失效。
            if (type.startsWith("feign.")) {
                try {
                    int remoteStatus = (Integer) cause.getClass().getMethod("status").invoke(cause);
                    if (java.util.Set.of(400,404,409,422).contains(remoteStatus))
                        return ResponseEntity.status(remoteStatus).body(ApiResponse.error("Dependent service rejected the request"));
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        if (dependencyFailure) {
            log.warn("Dependent service request failed ({})", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Dependent service is temporarily unavailable"));
        }
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error"));
    }
}
