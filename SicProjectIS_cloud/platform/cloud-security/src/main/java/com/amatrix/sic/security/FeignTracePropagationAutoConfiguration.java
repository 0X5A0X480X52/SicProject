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

@AutoConfiguration(after = CloudResourceServerAutoConfiguration.class)
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FeignTracePropagationAutoConfiguration {
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
