package com.amatrix.sic.security;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;

/**
 * 装配 Feign RequestInterceptor，将已规范化的 Trace/Saga/Event 标识从请求属性或 MDC 转发。
 */
@AutoConfiguration(after = CloudResourceServerAutoConfiguration.class)
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FeignTracePropagationAutoConfiguration {
    /**
     * 为所有 Servlet 服务中的 Feign 请求转发 X-Trace-Id、X-Saga-Id 和 X-Event-Id。
     * <p>优先使用入口过滤器已校验并写入 request attribute 的值；没有 Servlet 请求上下文时使用当前 MDC，支持定时任务等后台调用。
     * 只转发非空关联标识，不复制 Authorization、Cookie 或原始未规范化 Header。
     */
    @Bean
    @ConditionalOnMissingBean(name = "sicTraceRequestInterceptor")
    RequestInterceptor sicTraceRequestInterceptor() {
        return template -> {
            ServletRequestAttributes attributes = RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes servlet ? servlet : null;
            String traceId = attributes == null ? MDC.get("traceId")
                    : string(attributes.getRequest().getAttribute(RequestLoggingContextFilter.TRACE_ATTRIBUTE));
            relay(template, RequestLoggingContextFilter.TRACE_HEADER, traceId);
            relay(template, RequestLoggingContextFilter.SAGA_HEADER,
                    attributes == null ? MDC.get("sagaId")
                            : string(attributes.getRequest().getAttribute(RequestLoggingContextFilter.SAGA_ATTRIBUTE)));
            relay(template, RequestLoggingContextFilter.EVENT_HEADER,
                    attributes == null ? MDC.get("eventId")
                            : string(attributes.getRequest().getAttribute(RequestLoggingContextFilter.EVENT_ATTRIBUTE)));
        };
    }

    private static void relay(feign.RequestTemplate template, String name, String value) {
        if (value != null && !value.isBlank()) template.header(name, value);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
