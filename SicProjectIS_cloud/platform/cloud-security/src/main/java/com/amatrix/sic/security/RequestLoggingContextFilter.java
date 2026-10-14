package com.amatrix.sic.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 为 Servlet 请求建立 Trace/Saga/Event/User MDC 与请求属性，回传 Trace Header，并在结束时恢复原 MDC。
 */
public final class RequestLoggingContextFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RequestLoggingContextFilter.class);
    private final boolean evidenceEnabled;
    private final String service;
    private final String instance;

    public RequestLoggingContextFilter() { this(false, "unknown", "unknown"); }
    public RequestLoggingContextFilter(boolean enabled, String service, String instance) {
        this.evidenceEnabled = enabled; this.service = service; this.instance = instance;
    }
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String SAGA_HEADER = "X-Saga-Id";
    public static final String EVENT_HEADER = "X-Event-Id";
    public static final String TRACE_ATTRIBUTE = RequestLoggingContextFilter.class.getName() + ".traceId";
    public static final String SAGA_ATTRIBUTE = RequestLoggingContextFilter.class.getName() + ".sagaId";
    public static final String EVENT_ATTRIBUTE = RequestLoggingContextFilter.class.getName() + ".eventId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        long started = System.nanoTime();
        String traceId = bounded(request.getHeader(TRACE_HEADER), 8, 80);
        if (traceId == null) traceId = UUID.randomUUID().toString();
        String sagaId = bounded(request.getHeader(SAGA_HEADER), 1, 160);
        String eventId = bounded(request.getHeader(EVENT_HEADER), 1, 160);
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        request.setAttribute(SAGA_ATTRIBUTE, sagaId);
        request.setAttribute(EVENT_ATTRIBUTE, eventId);
        response.setHeader(TRACE_HEADER, traceId);
        MDC.put("traceId", traceId);
        put("sagaId", sagaId);
        put("eventId", eventId);
        if (request.getUserPrincipal() instanceof JwtAuthenticationToken authentication) {
            put("userId", bounded(authentication.getToken().getSubject(), 1, 80));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (evidenceEnabled) {
                // 只记录路径，不记录 query、Header 或请求体；长连接仅记录建立请求的处理时间。
                LOG.info("SIC_REQUEST_EVIDENCE service={} instance={} trace={} method={} path={} status={} durationMs={}",
                        service, instance, traceId, request.getMethod(), request.getRequestURI(), response.getStatus(),
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }

    private static void put(String key, String value) {
        if (value != null) MDC.put(key, value); else MDC.remove(key);
    }

    private static String bounded(String value, int minimum, int maximum) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() >= minimum && normalized.length() <= maximum
                && normalized.matches("[A-Za-z0-9:_-]+") ? normalized : null;
    }
}
